export type TrafficQueryType =
  | 'PROVINCE_OVERVIEW'
  | 'ROUTE_CATALOG'
  | 'PROVINCE_ABNORMAL'
  | 'CITY_PAIR'
  | 'ROUTE_DETAIL'
  | 'CAPACITY_OVERVIEW'
  | 'CAPACITY_BOTTLENECKS'
  | 'CAPACITY_ROUTE_DETAIL'
  | 'REGIONAL_TRAFFIC_OVERVIEW'
  | 'REGIONAL_PAIR_PRESSURE'
  | 'REGIONAL_KEY_CHANNELS'
  | 'VEHICLE_PATTERN_OVERVIEW'
  | 'VEHICLE_STRUCTURE'
  | 'VEHICLE_HOURLY_PATTERN'
  | 'VEHICLE_DAY_TYPE_COMPARISON'
  | 'OD_DESTINATION_TENDENCY'
  | 'OD_CONNECTION_MATRIX'

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
  analysisDate?: string
  includeTrend?: boolean
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

export interface RegionalPairRow {
  cityARegionCode: string; cityAName: string; cityBRegionCode: string; cityBName: string
  routeCount: number; checkpointCount: number; weeklyTotalFlow: number
  dailyAverageFlow: number; averageSpeedKmh: number
}

export interface RegionalChannelRow {
  cityARegionCode: string; cityAName: string; cityBRegionCode: string; cityBName: string
  routeCode: string; routeName: string; checkpointCount: number; weeklyTotalFlow: number
  dailyAverageFlow: number; averageSpeedKmh: number
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
  regionalPairRows?: RegionalPairRow[]
  regionalChannelRows?: RegionalChannelRow[]
  totalRegionalPairCount?: number
  totalRegionalChannelCount?: number
  analysisCity?: string
  vehicleStructureRows?: VehicleStructureRow[]
  vehicleTimeFeatureRows?: VehicleTimeFeatureRow[]
  vehicleDayTypeRows?: VehicleDayTypeRow[]
  hourlyVehicleSeries?: HourlyVehicleFlow[]
  odDestinationRows?: OdDestinationTendencyRow[]
  odMatrixRows?: OdMatrixRow[]
  totalSegmentCount: number
  displayedSegmentCount: number
  truncated: boolean
  source: 'MYSQL'
  acquiredAt: string
  warnings: string[]
  traceId: string
}

export interface OdDestinationTendencyRow {
  analysisRegionCode: string
  analysisCityName: string
  destinationRegionCode: string
  destinationCityName: string
  routeCount: number
  weeklyConnectionStrength: number
  tendencyRatio: number
}

export interface OdMatrixCell {
  destinationRegionCode: string
  destinationCityName: string
  weeklyConnectionStrength?: number
  tendencyRatio?: number
}

export interface OdMatrixRow {
  analysisRegionCode: string
  analysisCityName: string
  cells: OdMatrixCell[]
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
