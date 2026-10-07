import { NativeModule, requireNativeModule } from 'expo';

export type NativeProxyType = 'http' | 'socks';

declare class TwitchNetModule extends NativeModule<{}> {
  setProxy(host: string | null, port: number, type: NativeProxyType): void;
  testProxy(
    host: string,
    port: number,
    type: NativeProxyType,
  ): Promise<{ status: number; ms: number }>;
}

export default requireNativeModule<TwitchNetModule>('TwitchNet');
