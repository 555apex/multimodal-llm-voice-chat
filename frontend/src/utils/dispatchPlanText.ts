const RESOURCE_SCHEDULE_HEADING = /(?:^|\r?\n)\s*数据库资源调度安排[:：]/m

/**
 * 数据库匹配结果由独立的资源卡片展示，救援方案区域只保留模型生成的处置方案。
 * 后端保存的完整方案快照不会因此被修改。
 */
export function rescuePlanForDisplay(value?: string | null): string {
  const text = value?.trim() ?? ''
  const scheduleHeading = RESOURCE_SCHEDULE_HEADING.exec(text)
  return scheduleHeading ? text.slice(0, scheduleHeading.index).trimEnd() : text
}

/** 数据库保留完整资源池名称，界面使用更简洁的调度资源名称。 */
export function resourceNameForDisplay(value?: string | null): string {
  return (value?.trim() ?? '').replace(/\s*资源池\s*$/, '')
}

/** 历史通告补充来源城市时，避免“厦门厦门市……”这类重复前缀。 */
export function resourceNameWithCityForDisplay(
  sourceCityName: string,
  resourceName: string,
): string {
  const displayName = resourceNameForDisplay(resourceName)
  const cityPrefix = sourceCityName.endsWith('市')
    ? sourceCityName.slice(0, -1)
    : sourceCityName
  return displayName.startsWith(cityPrefix)
    ? displayName
    : `${sourceCityName}${displayName}`
}
