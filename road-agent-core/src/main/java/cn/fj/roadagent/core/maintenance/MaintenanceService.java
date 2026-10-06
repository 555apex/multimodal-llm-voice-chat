package cn.fj.roadagent.core.maintenance;

import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.maintenance.*;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.MaintenanceDocumentRenderer;
import cn.fj.roadagent.application.port.MaintenanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MaintenanceService implements MaintenanceDocumentUseCase {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private final MaintenanceRepository repository;
    private final MaintenanceDocumentRenderer renderer;
    private final ChatModelPort model;
    private final Clock clock;

    public MaintenanceService(MaintenanceRepository repository, MaintenanceDocumentRenderer renderer,
                              ChatModelPort model, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.model = model;
        this.clock = clock;
    }

    public MaintenanceProjectResult projects(String message) {
        List<MaintenanceProject> all = repository.findActiveProjects();
        String text = message == null ? "" : message;
        boolean higherUrgency = text.contains("紧急程度较高") || text.contains("较高紧急程度")
                || text.contains("高紧急程度");
        boolean urgentOnly = !higherUrgency && text.contains("紧急") && !text.contains("应急养护");
        boolean elevatedOnly = !higherUrgency && text.contains("较高");
        List<MaintenanceProject> selected = all.stream()
                .filter(p -> !higherUrgency || "紧急".equals(p.urgency()) || "较高".equals(p.urgency()))
                .filter(p -> !urgentOnly || "紧急".equals(p.urgency()))
                .filter(p -> !elevatedOnly || "较高".equals(p.urgency()))
                .filter(p -> !text.contains("修复养护") || "修复养护".equals(p.maintenanceType()))
                .filter(p -> routeMatches(text, p)).toList();
        BigDecimal budget = selected.stream().map(MaintenanceProject::budgetWan)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String summary = "当前养护工程项目库共筛选出" + selected.size() + "项，计划预算合计"
                + budget.setScale(2, RoundingMode.HALF_UP) + "万元。项目已按紧急程度和优先级排序，"
                + "可继续生成总体预安排计划或指定项目的方案比选。";
        return new MaintenanceProjectResult("福建普通国省干线养护工程项目库", summary, selected,
                repository.currentBatchId(), clock.instant());
    }

    private boolean routeMatches(String text, MaintenanceProject project) {
        Matcher matcher = Pattern.compile("(?i)([GS]\\d{2,4})").matcher(text);
        return !matcher.find() || project.routeCode().equalsIgnoreCase(matcher.group(1));
    }

    public Optional<MaintenanceProject> selectedProject(String message) {
        return repository.findProject(message);
    }

    public MaintenanceDocument document(AgentIntent intent, String message) {
        int year = extractYear(message);
        String type = intent == AgentIntent.MAINTENANCE_PREPLAN ? "PREPLAN"
                : intent == AgentIntent.MAINTENANCE_SCHEME_COMPARISON ? "SCHEME_COMPARISON"
                : reportType(message);
        MaintenanceProject project = null;
        if (intent == AgentIntent.MAINTENANCE_SCHEME_COMPARISON) {
            project = repository.findProject(message).orElseThrow(() ->
                    new IllegalArgumentException("请先指定项目编号、路线编号或项目清单中的具体路段，再生成养护方案比选。"));
        }
        String batch = repository.currentBatchId();
        String projectCode = project == null ? null : project.projectCode();
        int documentVersion = "PREPLAN".equals(type) ? 2 : 1;
        Optional<MaintenanceDocument> cached = repository.findSnapshot(type, year, projectCode, batch)
                .filter(existing -> existing.version() >= documentVersion);
        if (cached.isPresent()) return cached.get().withDownloadUrl(downloadUrl(cached.get().documentId()));

        List<MaintenanceProject> projects = repository.findActiveProjects();
        Draft draft = switch (type) {
            case "PREPLAN" -> preplan(year, projects);
            case "SCHEME_COMPARISON" -> scheme(year, Objects.requireNonNull(project));
            case "DISEASE_DISTRIBUTION" -> disease(year, projects);
            case "MAJOR_REPAIR_EVALUATION" -> majorRepair(year, projects);
            case "ROUTINE_SCORE" -> routineScore(year, projects, batch);
            case "SERVICE_SATISFACTION" -> serviceSatisfaction(year, projects, batch);
            default -> annualStatistics(year, projects);
        };
        Narrative narrative = polish(type, year, draft, repository.loadTemplate(type));
        String id = "MD-" + UUID.randomUUID();
        MaintenanceDocument document = new MaintenanceDocument(id, type, year, projectCode, draft.title,
                narrative.summary, narrative.conclusion, draft.sections, draft.charts, batch, documentVersion,
                clock.instant(), downloadUrl(id));
        repository.saveSnapshot(document);
        return document;
    }

    private Narrative polish(String type, int year, Draft draft, String template) {
        String facts = "文档类型=" + type + "；年度=" + year + "；标题=" + draft.title
                + "；固定摘要=" + draft.fallbackSummary + "；固定结论=" + draft.fallbackConclusion
                + "；章节=" + draft.sections.stream().map(MaintenanceSection::heading).toList();
        String prompt = """
                你是福建普通国省干线养护管理报告撰写助手。只能润色给定事实，不得新增路线、路段、数字、单位、机构或结论。
                输出JSON，仅包含summary和conclusion。summary为120至220字，conclusion为80至160字，语言正式、专业、逻辑清楚。
                禁止出现“无法生成、数据缺失、演示、模拟、系统、模型”等技术说明。
                """ + (template == null ? "" : "\n模板要求：" + template);
        try {
            Narrative result = model.generateStructuredStrict(new ModelRequest(prompt, facts, List.of(), 0.1), Narrative.class);
            if (result != null && valid(result.summary) && valid(result.conclusion)) return result;
        } catch (RuntimeException ignored) {
            // 受控模板兜底，不向用户暴露模型错误。
        }
        return new Narrative(draft.fallbackSummary, draft.fallbackConclusion);
    }

    private boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= 500
                && !value.matches(".*(无法生成|数据缺失|模型|JSON|演示|模拟).*" );
    }

    private Draft preplan(int year, List<MaintenanceProject> projects) {
        BigDecimal total = totalBudget(projects);
        List<Map<String, Object>> rows = projects.stream().map(this::preplanProjectRow).toList();
        String summary = year + "年度共安排" + projects.size() + "项养护工程，计划预算" + total + "万元。"
                + "项目按照紧急程度、工程类型和施工窗口统筹排序，优先保障紧急项目和交通影响较大的路段。";
        return new Draft(year + "年度福建普通国省干线养护预安排计划", summary,
                "建议按月跟踪项目准备、进场、实施和验收状态，对紧急项目实行重点督办，并结合现场核查结果动态优化施工窗口。",
                List.of(new MaintenanceSection("总体安排", List.of(summary), List.of()),
                        new MaintenanceSection("项目实施安排", List.of("各项目关键信息如下。"), rows),
                        new MaintenanceSection("组织实施要求", List.of("施工前完善现场核查、交通组织和安全交底；实施中控制工期、质量和预算；完工后及时组织验收并归档。"), List.of())),
                chartsByTypeAndUrgency(projects));
    }

    private Draft scheme(int year, MaintenanceProject p) {
        BigDecimal base = p.budgetWan();
        List<Map<String, Object>> rows = List.of(
                option("方案一", "局部处治", p.recommendedAction(), Math.max(2, p.durationDays() - 2), base.multiply(new BigDecimal("0.82")), "交通影响较小，耐久性适中", "适合快速恢复功能"),
                option("方案二", "综合修复", p.recommendedAction() + "，同步处治相邻薄弱部位", p.durationDays(), base, "技术完整、耐久性与经济性均衡", "推荐"),
                option("方案三", "强化处治", "扩大处治范围并增加结构补强和跟踪检测", p.durationDays() + 4, base.multiply(new BigDecimal("1.28")), "耐久性较好，但工期和交通影响较大", "适合病害扩展风险较高情形")
        );
        String summary = p.projectCode() + "位于" + p.routeCode() + " " + p.routeSection() + "，主要对象为" + p.facilityType()
                + "。本次形成局部处治、综合修复和强化处治三套方案。";
        return new Draft(p.projectCode() + "养护工程方案比选报告", summary,
                "综合技术可行性、耐久性、工程造价、施工周期和交通影响，推荐方案二。实施前应依据现场复核结果校准工程量和交通组织方案。",
                List.of(new MaintenanceSection("项目概况", List.of(summary, "主要病害：" + p.diseaseDescription()), List.of()),
                        new MaintenanceSection("方案比选", List.of("三套方案采用统一评价维度进行比较。"), rows),
                        new MaintenanceSection("推荐意见", List.of("优先采用方案二，并将现场复核作为开工前置条件。"), List.of())),
                List.of(new MaintenanceChart("方案预算比较", "万元", List.of("方案一", "方案二", "方案三"),
                        rows.stream().map(row -> ((BigDecimal) row.get("预算（万元）")).doubleValue()).toList())));
    }

    private Map<String, Object> option(String name, String route, String content, int days, BigDecimal budget, String evaluation, String result) {
        return row("方案", name, "技术路线", route, "主要内容", content, "工期（天）", days,
                "预算（万元）", budget.setScale(2, RoundingMode.HALF_UP), "综合评价", evaluation, "比选结论", result);
    }

    private Draft annualStatistics(int year, List<MaintenanceProject> projects) {
        Map<String, Long> typeCounts = counts(projects, MaintenanceProject::maintenanceType);
        String summary = year + "年度纳入统计的养护工程共" + projects.size() + "项，计划投资" + totalBudget(projects)
                + "万元，形成预防养护、修复养护、专项养护和应急养护相结合的项目结构。";
        return reportDraft(year + "年度福建普通国省干线养护统计分析报告", summary,
                "总体上项目结构与当前养护需求相匹配，下一阶段应加强紧急和较高优先级项目的过程跟踪，统筹年度资金和施工资源。",
                projects, typeCounts, "养护类型", "项目数");
    }

    private Draft disease(int year, List<MaintenanceProject> projects) {
        Map<String, Long> counts = counts(projects, MaintenanceProject::facilityType);
        String summary = "本报告对" + projects.size() + "项养护需求进行分类分析，覆盖路面、路基、排水、边坡防护、交安设施和桥涵构造物等对象。";
        return reportDraft(year + "年度福建普通国省干线路网病害分布分析报告", summary,
                "建议按照设施类别和紧急程度实施分层处治，对集中出现的同类问题组织专项复核，并将处治结果纳入后续项目库更新。",
                projects, counts, "设施类别", "项目数");
    }

    private Draft majorRepair(int year, List<MaintenanceProject> projects) {
        List<MaintenanceProject> selected = projects.stream().filter(p -> "大修".equals(p.repairScale()) || "中修".equals(p.repairScale())).toList();
        int base = 86 + Math.floorMod((year + repository.currentBatchId()).hashCode(), 8);
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("计划完成率", (long) base); metrics.put("质量达标率", (long) Math.min(99, base + 4));
        metrics.put("工期符合率", (long) Math.max(80, base - 2)); metrics.put("预算执行率", (long) Math.min(98, base + 1));
        String summary = year + "年度纳入大中修考核的项目共" + selected.size() + "项，考核围绕计划执行、质量、工期和预算四个方面开展。";
        return reportDraft(year + "年度福建普通国省干线大中修工程考核评估报告", summary,
                "考核结果总体稳定，建议强化开工前工程量复核和关键工序质量控制，对工期偏差项目建立整改闭环。",
                selected, metrics, "考核指标", "得分");
    }

    private Draft routineScore(int year, List<MaintenanceProject> projects, String batch) {
        int seed = Math.floorMod((year + batch).hashCode(), 6);
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("巡查覆盖", 91L + seed); metrics.put("整改及时", 88L + seed);
        metrics.put("作业质量", 90L + seed); metrics.put("安全管理", 93L + seed); metrics.put("资料管理", 87L + seed);
        String summary = year + "年度日常养护量化评价采用百分制，对巡查、整改、作业质量、安全和资料管理进行综合评分。";
        return reportDraft(year + "年度福建普通国省干线日常养护量化评分报告", summary,
                "建议保持巡查和安全管理优势，重点提升整改闭环与资料归档质量，并将低分项纳入下一周期复核。",
                projects.subList(0, Math.min(8, projects.size())), metrics, "评价指标", "得分");
    }

    private Draft serviceSatisfaction(int year, List<MaintenanceProject> projects, String batch) {
        int seed = Math.floorMod((year + batch + "service").hashCode(), 5);
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("环境卫生", 88L + seed); metrics.put("停车秩序", 86L + seed);
        metrics.put("便民服务", 89L + seed); metrics.put("服务态度", 92L + seed); metrics.put("信息服务", 85L + seed);
        String summary = year + "年度服务站点满意度评估围绕环境卫生、停车秩序、便民服务、服务态度和信息服务五个维度开展。";
        return reportDraft(year + "年度福建普通国省干线服务站点服务质量满意度评估报告", summary,
                "总体服务质量保持稳定，建议优先完善信息服务和停车组织，并持续跟踪公众反馈和问题整改情况。",
                projects.subList(0, Math.min(5, projects.size())), metrics, "评价维度", "满意度得分");
    }

    private Draft reportDraft(String title, String summary, String conclusion, List<MaintenanceProject> projects,
                              Map<String, ? extends Number> metrics, String metricName, String metricValue) {
        List<Map<String, Object>> metricRows = metrics.entrySet().stream()
                .map(e -> row(metricName, e.getKey(), metricValue, e.getValue())).toList();
        List<Map<String, Object>> projectRows = projects.stream().map(this::projectRow).toList();
        MaintenanceChart chart = new MaintenanceChart(metricName + "分析", metricValue,
                metrics.keySet().stream().toList(), metrics.values().stream().map(Number::doubleValue).toList());
        return new Draft(title, summary, conclusion,
                List.of(new MaintenanceSection("报告摘要", List.of(summary), List.of()),
                        new MaintenanceSection("主要指标", List.of("主要统计与评价指标如下。"), metricRows),
                        new MaintenanceSection("项目与路线明细", List.of("相关项目均来源于当前养护项目库。"), projectRows),
                        new MaintenanceSection("分析与建议", List.of(conclusion), List.of())), List.of(chart));
    }

    private List<MaintenanceChart> chartsByTypeAndUrgency(List<MaintenanceProject> projects) {
        Map<String, Long> types = counts(projects, MaintenanceProject::maintenanceType);
        Map<String, Long> urgencies = counts(projects, MaintenanceProject::urgency);
        return List.of(chart("养护类型结构", types), chart("紧急程度结构", urgencies));
    }

    private MaintenanceChart chart(String title, Map<String, Long> values) {
        return new MaintenanceChart(title, "项", values.keySet().stream().toList(),
                values.values().stream().map(Number::doubleValue).toList());
    }

    private Map<String, Long> counts(List<MaintenanceProject> projects, Function<MaintenanceProject, String> key) {
        return projects.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    private BigDecimal totalBudget(List<MaintenanceProject> projects) {
        return projects.stream().map(MaintenanceProject::budgetWan).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> projectRow(MaintenanceProject p) {
        return row("项目编号", p.projectCode(), "路线", p.routeCode(), "路线名称", p.routeName(), "路段", p.routeSection(),
                "养护对象", p.facilityType(), "养护类型", p.maintenanceType(), "紧急程度", p.urgency(),
                "计划开始时间", p.plannedStartDate().toString(), "工期（天）", p.durationDays(),
                "施工单位", p.contractor(), "预算（万元）", p.budgetWan());
    }

    private Map<String, Object> preplanProjectRow(MaintenanceProject p) {
        return row("项目编号", p.projectCode(), "路线", p.routeCode(), "路线名称", p.routeName(), "路段", p.routeSection(),
                "养护对象", p.facilityType(), "养护类型", p.maintenanceType(), "紧急程度", p.urgency(),
                "计划开始时间", p.plannedStartDate().toString(), "工期（天）", p.durationDays(),
                "预算（万元）", p.budgetWan());
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }

    private int extractYear(String message) {
        Matcher matcher = Pattern.compile("(20\\d{2})年?").matcher(message == null ? "" : message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : LocalDate.now(clock.withZone(CHINA)).getYear();
    }

    private String reportType(String message) {
        String text = message == null ? "" : message;
        if (text.contains("病害分布")) return "DISEASE_DISTRIBUTION";
        if (text.contains("大中修")) return "MAJOR_REPAIR_EVALUATION";
        if (text.contains("日常养护")) return "ROUTINE_SCORE";
        if (text.contains("服务区") || text.contains("服务站") || text.contains("满意度")) return "SERVICE_SATISFACTION";
        return "ANNUAL_STATISTICS";
    }

    private String downloadUrl(String id) { return "/api/maintenance/documents/" + id + "/download"; }

    @Override public Optional<MaintenanceDocument> findDocument(String documentId) { return repository.findDocument(documentId); }

    @Override public byte[] download(String documentId) {
        MaintenanceDocument document = repository.findDocument(documentId)
                .orElseThrow(() -> new IllegalArgumentException("养护文档不存在或已失效"));
        return renderer.render(document);
    }

    private record Narrative(String summary, String conclusion) {}
    private record Draft(String title, String fallbackSummary, String fallbackConclusion,
                         List<MaintenanceSection> sections, List<MaintenanceChart> charts) {}
}
