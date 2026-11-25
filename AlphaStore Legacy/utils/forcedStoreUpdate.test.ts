import { describe, expect, it } from 'vitest';
import {
  CURRENT_FORCED_STORE_UPDATE_VERSION,
  buildForcedStoreFileName,
  canResumeForcedStoreUpdate,
  createForcedStoreUpdateSnapshot,
  isStoreUpdateAvailable,
  shouldForceStoreUpdate
} from './forcedStoreUpdate';
import type { ForcedStoreUpdateSnapshot } from './forcedStoreUpdate';

describe('forced store update helpers', () => {
  it('marks AlphaStore update as available when the latest version is newer', () => {
    expect(isStoreUpdateAvailable('1.3.3', '1.3.4')).toBe(true);
    expect(isStoreUpdateAvailable('1.3.4', '1.3.4')).toBe(false);
  });

  it('forces AlphaStore update only when the minimum supported version is newer', () => {
    expect(shouldForceStoreUpdate('1.3.3', '1.3.4')).toBe(true);
    expect(shouldForceStoreUpdate('1.3.3', '1.3.3')).toBe(false);
  });

  it('builds a stable AlphaStore APK file name', () => {
    expect(buildForcedStoreFileName('1.3.4')).toBe('AlphaStore_1.3.4.apk');
  });

  it('restores only matching completed downloads', () => {
    const snapshot = createForcedStoreUpdateSnapshot({
      targetVersion: '1.3.4',
      downloadFileName: 'AlphaStore_1.3.4.apk',
      status: 'ready_to_scan_or_install',
      downloadCompleted: true,
      exported: false
    });

    expect(
      canResumeForcedStoreUpdate(snapshot, {
        targetVersion: '1.3.4',
        minStoreVersion: '1.3.4',
        currentVersion: '1.3.3'
      })
    ).toBe(true);

    expect(
      canResumeForcedStoreUpdate(snapshot, {
        targetVersion: '1.3.5',
        minStoreVersion: '1.3.4',
        currentVersion: '1.3.3'
      })
    ).toBe(false);
  });

  it('pins the snapshot schema version', () => {
    expect(CURRENT_FORCED_STORE_UPDATE_VERSION).toBe(1);
  });

  it('accepts persisted snapshots with export state', () => {
    const snapshot: ForcedStoreUpdateSnapshot = {
      schemaVersion: 1,
      targetVersion: '1.3.4',
      downloadFileName: 'AlphaStore_1.3.4.apk',
      status: 'export_ready',
      downloadCompleted: true,
      exported: true
    };

    expect(snapshot.status).toBe('export_ready');
    expect(snapshot.exported).toBe(true);
  });
});
