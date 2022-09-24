import { SettingKey } from '@core/domain';

export interface SettingTheme {
  readonly name: string;
  readonly key: string;
  readonly settings: Record<SettingKey, any>;
}