import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.gastech.pos',
  appName: 'GasTech POS',
  webDir: 'dist',
  server: {
    // ✅ الإنتاج: يحمّل الموقع من Lovable مباشرة (تحديثات فورية)
    url: 'https://gastech.lovable.app',
    cleartext: false,
    androidScheme: 'https',
    allowNavigation: [
      'gastech.lovable.app',
      '*.lovable.app',
      '*.supabase.co',
    ],
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: true,
  },
  plugins: {
    Geolocation: {
      // صلاحيات GPS تُطلب Native
    },
    BluetoothLe: {
      displayStrings: {
        scanning: 'جاري البحث عن طابعات...',
        cancel: 'إلغاء',
        availableDevices: 'الطابعات المتاحة',
        noDeviceFound: 'لم يتم العثور على طابعات',
      },
    },
    SplashScreen: {
      launchShowDuration: 1500,
      backgroundColor: '#0F172A',
      showSpinner: false,
    },
  },
};

export default config;
