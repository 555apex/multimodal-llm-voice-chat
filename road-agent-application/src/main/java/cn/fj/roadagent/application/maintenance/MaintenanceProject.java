package cn.fj.roadagent.application.maintenance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceProject(
        String projectCode,
        String routeCode,
        String routeName,
        String routeSection,
        String facilityType,
        String maintenanceType,
        String repairScale,
        String diseaseDescription,
        String recommendedAction,
        String urgency,
        int priority,
        LocalDate plannedStartDate,
        int durationDays,
        String contractor,
        BigDecimal budgetWan
) {}
