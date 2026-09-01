package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.exception.BusinessRuleException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 模型只能选择此目录中已注册的Skill，用final修饰 */
public final class SkillRegistry {
    private final Map<AgentIntent, AgentSkill> skills = new EnumMap<>(AgentIntent.class);   // Intent和Skill通过Map对应起来存储
    /*
    EnumMap 是Java专门为枚举键优化的Map实现。内部用数组存储（不是哈希表），
    因为枚举值的数量在编译期就确定了，所以 EnumMap 查询更快、更省内存
     */

    // 构造函数
    /*
    Spring启动时，会把所有实现了 AgentSkill 接口的Bean（HighwayTrafficSkill、EmergencyDispatchSkill）注入这个列表
    skills.put() 的返回值检查：put 返回旧的Skill → 非null → 抛异常，防止一个意图被两个Skill同时注册。
     */
    public SkillRegistry(List<AgentSkill> registeredSkills) {
        for (AgentSkill skill : registeredSkills) {
            if (skills.put(skill.intent(), skill) != null) {
                throw new IllegalArgumentException("重复注册Skill：" + skill.intent());
            }
        }
    }

    // 方法：防止需要的业务Skill未注册，抛出异常
    public AgentSkill require(AgentIntent intent) {
        AgentSkill skill = skills.get(intent);
        if (skill == null) {
            throw new BusinessRuleException("SKILL_NOT_REGISTERED", "该业务能力尚未注册");
        }
        return skill;
    }
}
