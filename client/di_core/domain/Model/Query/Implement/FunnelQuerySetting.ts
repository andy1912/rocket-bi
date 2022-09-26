/*
 * @author: tvc12 - Thien Vi
 * @created: 12/3/20, 10:39 AM
 */

import {
  Condition,
  Equal,
  Filterable,
  Function,
  FunnelChartOption,
  getFiltersAndSorts,
  InlineSqlView,
  OrderBy,
  QuerySettingType,
  TableColumn,
  WidgetId,
  Zoomable
} from '@core/domain/Model';
import { isEqual } from 'lodash';
import { QuerySetting } from '../QuerySetting';
import { Drilldownable, DrilldownData } from '@core/domain/Model/Query/Features/Drilldownable';
import { ZoomData } from '@/shared';
import { ConditionUtils } from '@core/utils';
import { ConfigDataUtils } from '@/screens/ChartBuilder/ConfigBuilder/ConfigPanel/ConfigDataUtils';

export class FunnelQuerySetting extends QuerySetting<FunnelChartOption> implements Zoomable, Drilldownable, Filterable {
  readonly className = QuerySettingType.Funnel;

  constructor(
    public legend: TableColumn,
    public value: TableColumn,
    filters: Condition[] = [],
    sorts: OrderBy[] = [],
    options: Record<string, any> = {},
    sqlViews: InlineSqlView[] = []
  ) {
    super(filters, sorts, options, sqlViews);
  }

  get zoomData(): ZoomData {
    return new ZoomData(this.legend.function);
  }

  static fromObject(obj: FunnelQuerySetting): FunnelQuerySetting {
    const [filters, sorts] = getFiltersAndSorts(obj);
    const legend = TableColumn.fromObject(obj.legend);
    const value = TableColumn.fromObject(obj.value);
    const sqlViews: InlineSqlView[] = (obj.sqlViews ?? []).map((view: any) => InlineSqlView.fromObject(view));

    return new FunnelQuerySetting(legend, value, filters, sorts, obj.options, sqlViews);
  }

  getAllFunction(): Function[] {
    return [this.legend.function, this.value.function];
  }

  getAllTableColumn(): TableColumn[] {
    return [this.legend, this.value];
  }

  buildQueryDrilldown(drilldownData: DrilldownData): FunnelQuerySetting {
    const newLegend: TableColumn = this.legend.copyWith({
      name: drilldownData.name,
      fieldRelatedFunction: drilldownData.toField
    });
    const currentConditions: Condition[] = this.filters ?? [];
    const equal: Equal = ConditionUtils.buildEqualCondition(this.legend, drilldownData.value);
    const drilldownConditions: Condition[] = ConditionUtils.buildDrilldownConditions(currentConditions, equal);
    return new FunnelQuerySetting(newLegend, this.value, drilldownConditions, this.sorts, this.options, this.sqlViews);
  }

  getColumnWillDrilldown(): TableColumn {
    return this.legend;
  }

  getFilter(): TableColumn {
    return this.legend;
  }

  buildNewZoomData(data: ZoomData, nextLvl: string): ZoomData {
    return data.createNewHorizontalField(nextLvl);
  }

  setZoomData(data: ZoomData): void {
    if (data.horizontalLevel?.scalarFunction) {
      const newScalarFn = data.horizontalLevel.scalarFunction;
      this.sorts.filter(sort => isEqual(sort.function.field, data.horizontalLevel.field)).forEach(sort => sort.function.setScalarFunction(newScalarFn));
      this.legend.function.setScalarFunction(newScalarFn);
    }
  }

  setDynamicFunctions(functions: Map<WidgetId, TableColumn[]>): void {
    this.legend = ConfigDataUtils.replaceDynamicFunction(this.legend, functions);
    this.value = ConfigDataUtils.replaceDynamicFunction(this.value, functions);
  }
}