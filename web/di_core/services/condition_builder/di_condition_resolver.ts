import { ConditionResolver } from '@core/services/condition_builder/condition_resolver';
import { ListUtils, MapUtils } from '@/utils';
import { And, Condition, DynamicValueCondition, DynamicConditionWidget, FieldRelatedCondition, GetArrayElement } from '@core/domain/Model';
import { ConditionData } from '@/shared';
import { ConditionBuilder } from '@core/services/condition_builder/condition_builder';
import { Inject } from 'typescript-ioc';
import { Log } from '@core/utils';

export class DiConditionResolver implements ConditionResolver {
  @Inject
  private builder!: ConditionBuilder;

  buildCondition(index: number, conditionData: ConditionData): Condition | undefined {
    const condition = this.builder.buildCondition(conditionData);
    Log.debug('DiConditionResolver::buildCondition', condition);
    if (condition && FieldRelatedCondition.isFieldRelatedCondition(condition) && conditionData.isNested) {
      if (condition.scalarFunction) {
        condition.scalarFunction.withScalarFunction(new GetArrayElement());
      } else {
        condition.setScalarFunction(new GetArrayElement());
      }
    }
    return condition;
  }

  buildConditions(mapConditionData: Map<number, ConditionData[]>): Condition[] {
    return MapUtils.map(mapConditionData, (key, rawConditions) => {
      const conditions = rawConditions.map((condition, index) => this.buildCondition(index, condition)).filter((item): item is FieldRelatedCondition => !!item);
      if (ListUtils.isNotEmpty(conditions)) {
        return new And(conditions);
      } else {
        return void 0;
      }
    }).filter((item): item is And => !!item);
  }
}