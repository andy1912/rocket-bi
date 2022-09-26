import { Config, IncrementalConfig } from '@core/DataCook';
import { StringUtils } from '@/utils/string.utils';
import { Log } from '@core/utils';

export class EtlConfig {
  constructor(public mapIncrementalConfig: Map<string, Config>) {}

  static fromObject(obj: EtlConfig) {
    const result = new Map<string, Config>();
    Log.debug('EtlConfig::fromObject::obj::', obj);
    Object.entries(obj.mapIncrementalConfig).forEach(([key, value]: any) => {
      result.set(StringUtils.toSnakeCase(key), Config.fromObject(value));
    });
    return new EtlConfig(result);
  }
}