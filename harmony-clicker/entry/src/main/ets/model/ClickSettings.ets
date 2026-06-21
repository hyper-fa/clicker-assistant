import preferences from '@ohos.data.preferences';
import type common from '@ohos.app.ability.common';

const STORE_NAME = 'click_settings';
const KEY_INTERVAL = 'intervalMs';
const KEY_X = 'targetX';
const KEY_Y = 'targetY';
const DEFAULT_INTERVAL = 100;

export interface ClickTarget {
  x: number;
  y: number;
}

export class ClickSettings {
  static async getIntervalMs(context: common.UIAbilityContext): Promise<number> {
    const store = await preferences.getPreferences(context, STORE_NAME);
    const value = await store.get(KEY_INTERVAL, DEFAULT_INTERVAL);
    return Math.max(1, Number(value) || DEFAULT_INTERVAL);
  }

  static async setIntervalMs(context: common.UIAbilityContext, intervalMs: number): Promise<void> {
    const store = await preferences.getPreferences(context, STORE_NAME);
    await store.put(KEY_INTERVAL, Math.max(1, Math.round(intervalMs)));
    await store.flush();
  }

  static async getTarget(context: common.UIAbilityContext): Promise<ClickTarget> {
    const store = await preferences.getPreferences(context, STORE_NAME);
    const x = Number(await store.get(KEY_X, 0)) || 0;
    const y = Number(await store.get(KEY_Y, 0)) || 0;
    return { x, y };
  }

  static async setTarget(context: common.UIAbilityContext, target: ClickTarget): Promise<void> {
    const store = await preferences.getPreferences(context, STORE_NAME);
    await store.put(KEY_X, Math.max(0, Math.round(target.x)));
    await store.put(KEY_Y, Math.max(0, Math.round(target.y)));
    await store.flush();
  }
}
