import { registerPlugin } from '@capacitor/core';

export interface SunmiPrinterPlugin {
  isAvailable(): Promise<{ available: boolean }>;
  printReceipt(options: { payload: string }): Promise<{ success: boolean }>;
  printText(options: { text: string }): Promise<void>;
  cutPaper(): Promise<void>;
  openCashDrawer(): Promise<void>;
}

export const SunmiPrinter = registerPlugin<SunmiPrinterPlugin>('SunmiPrinter');
