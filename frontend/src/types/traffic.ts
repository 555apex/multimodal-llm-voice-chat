export type TrafficQueryType =
  | 'PROVINCE_OVERVIEW'
  | 'PROVINCE_ABNORMAL'
  | 'CITY_PAIR'
  | 'ROUTE_DETAIL'
  | 'CAPACITY_OVERVIEW'
  | 'CAPACITY_BOTTLENECKS'
  | 'CAPACITY_ROUTE_DETAIL'
  | 'REGIONAL_TRAFFIC_OVERVIEW'
  | 'CHECKPOINT_PRESSURE'
  | 'CITY_PRESSURE'
  | 'ROUTE_PRESSURE'
  | 'VEHICLE_PATTERN_OVERVIEW'
  | 'VEHICLE_STRUCTURE'
  | 'VEHICLE_HOURLY_PATTERN'
  | 'VEHICLE_DAY_TYPE_COMPARISON'
  | 'OD_OVERVIEW'
  | 'OD_CITY_FLOW'
  | 'OD_KEY_CHANNELS'

export type TrafficStatus = 10 | 20 | 30 | 40 | 50
export type CapacityLevel = 'NORMAL' | 'BOTTLENECK' | 'SEVERE_BOTTLENECK'

export interface TrafficQueryPayload {
  queryType: TrafficQueryType
  originCity?: string
  destinationCity?: string
  routeCode?: string
  routeName?: string
  selectedCities?: string[]
  analysisCity?: string
}

export interface RouteTrafficSummary {
  routeCode: string
  routeName: string
  averageSpeedKmh: number
  status: TrafficStatus
  statusName: string
}

export interface TrafficSegment {
  routeCode: string
  routeName: string
  routeSection: string
  distanceKm?: number
  averageSpeedKmh: number
  status: TrafficStatus
  statusName: string
  severity: number
}

export interface RoadCapacityRow {
  routeCode: string
  routeName: string
  actualCapacityVph: number
  designCapacityVph: number
  utilizationRatio: number
  capacityLevel: CapacityLevel
  capacityLevelName: string
}

export interface SelectedRegion {
  regionCode: string
  regionName: string
}

export interface TransportHubRow {
  checkpointNo: string
  routeCode: string
  routeName: string
  averageSpeedKmh: number
  dailyAverageFlow: number
}

export interface RegionPressureRow {
  regionCode: string
  regionName: string
  activeHubCount: number
  totalDailyFlow: number
  hubShareRatio: number
  interpretation: string
}

export interface RoutePressureRow {
  routeCode: string
  routeName: string
  totalDailyFlow: number
  checkpointCount: number
  averageSpeedKmh: number
}

export type VehicleType = 'CAR' | 'BUS' | 'TRUCK'

export interface VehicleStructureRow {
  vehicleType: VehicleType
  vehicleTypeName: string
  weeklyVolume: number
  shareRatio: number
}

export interface VehicleTimeFeatureRow {
  vehicleType: VehicleType
  vehicleTypeName: string
  peakHour: string
  peakVolume: number
  morningPeakRatio: number
  eveningPeakRatio: number
  characteristic: string
}

export interface VehicleDayTypeRow {
  vehicleType: VehicleType
  vehicleTypeName: string
  weekdayVolume: number
  weekendVolume: number
}

export interface HourlyVehicleFlow {
  hour: string
  car: number
  bus: number
  truck: number
}

export interface TrafficQueryResult {
  queryType: TrafficQueryType
  title: string
  summary: string
  routeSummaries: RouteTrafficSummary[]
  segments: TrafficSegment[]
  capacityRows: RoadCapacityRow[]
  selectedRegions?: SelectedRegion[]
  hubRows?: TransportHubRow[]
  regionPressureRows?: RegionPressureRow[]
  routePressureRows?: RoutePressureRow[]
  analysisCity?: string
  vehicleStructureRows?: VehicleStructureRow[]
  vehicleTimeFeatureRows?: VehicleTimeFeatureRow[]
  vehicleDayTypeRows?: VehicleDayTypeRow[]
  hourlyVehicleSeries?: HourlyVehicleFlow[]
  odCityFlowRows?: OdCityFlowRow[]
  odChannelRows?: OdChannelRow[]
  periodDays?: number
  missingRegions?: SelectedRegion[]
  totalSegmentCount: number
  displayedSegmentCount: number
  truncated: boolean
  source: 'MYSQL'
  acquiredAt: string
  warnings: string[]
  traceId: string
}

export interface OdCityFlowRow {
  regionCode: string
  regionName: string
  checkpointCount: number
  weeklyTotalFlow: number
  dailyAverageFlow: number
  averageSpeedKmh: number
}

export interface OdChannelRow {
  routeCode: string
  routeName: string
  weeklyTotalFlow: number
  carWeeklyFlow: number
  busWeeklyFlow: number
  truckWeeklyFlow: number
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
