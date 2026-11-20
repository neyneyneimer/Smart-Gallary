import React, { useMemo } from "react";
import {
  Cloud,
  RefreshCw,
  CheckCircle,
  Shield,
  Tablet,
  Smartphone,
  Laptop,
  HardDrive,
  Database
} from "lucide-react";
import { PhotoEntity } from "../types";

interface CloudSyncProps {
  photos: PhotoEntity[];
  isSyncing: boolean;
  syncProgress: number;
  syncCurrent: number;
  syncTotal: number;
  syncActiveCloud: string;
  onChangeCloud: (cloud: string) => void;
  onStartSync: () => void;
}

export function CloudSync({
  photos,
  isSyncing,
  syncProgress,
  syncCurrent,
  syncTotal,
  syncActiveCloud,
  onChangeCloud,
  onStartSync
}: CloudSyncProps) {
  const unsyncedCount = useMemo(() => {
    return photos.filter((p) => !p.hasBackupSynced).length;
  }, [photos]);

  const syncedCount = useMemo(() => {
    return photos.filter((p) => p.hasBackupSynced).length;
  }, [photos]);

  const totalBytes = useMemo(() => {
    return photos.reduce((acc, p) => acc + p.sizeBytes, 0);
  }, [photos]);

  const syncedBytes = useMemo(() => {
    return photos.filter((p) => p.hasBackupSynced).reduce((acc, p) => acc + p.sizeBytes, 0);
  }, [photos]);

  const formatBytes = (bytes: number) => {
    if (bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  };

  const providers = [
    { name: "AuraCloud", label: "AuraCloud Storage", region: "Global Edge / E2EE" },
    { name: "Google Photos", label: "Google Photos Storage", region: "Google Workspace" },
    { name: "OneDrive", label: "OneDrive Storage", region: "Microsoft Azure" },
    { name: "Dropbox", label: "Dropbox Storage", region: "Dropbox Core" }
  ];

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Intro Header */}
      <div>
        <h2 className="text-xl font-bold text-stone-900 tracking-tight">Multi-Device Backup Sync</h2>
        <p className="text-xs text-stone-500 mt-1">
          Cryptographically synchronized across local devices and remote vaults with duplicate exclusion.
        </p>
      </div>

      {/* Target Cloud Provider Selector */}
      <div className="flex flex-col gap-3">
        <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wider pl-1">
          Active Backup Provider
        </h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {providers.map((provider) => {
            const isSelected = syncActiveCloud === provider.label;
            return (
              <button
                key={provider.name}
                onClick={() => onChangeCloud(provider.label)}
                className={`flex flex-col items-start p-4 rounded-2xl border transition cursor-pointer text-left ${
                  isSelected
                    ? "bg-stone-900 text-white border-stone-900 shadow-xs"
                    : "bg-white border-stone-200 hover:bg-stone-50 text-stone-700"
                }`}
              >
                <Cloud size={20} className={isSelected ? "text-stone-300" : "text-stone-400"} />
                <span className="text-xs font-semibold mt-2">{provider.name}</span>
                <span className={`text-[10px] mt-0.5 font-mono ${isSelected ? "text-stone-400" : "text-stone-500"}`}>
                  {provider.region}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Sync Integrity & Metric Status */}
      <div className="bg-white border border-stone-200 rounded-2xl p-5 shadow-xs flex flex-col gap-4">
        <div className="flex items-center justify-between border-b border-stone-200 pb-3">
          <div className="flex items-center gap-2">
            <Shield size={16} className="text-stone-700" />
            <h3 className="text-xs font-semibold uppercase tracking-wider text-stone-900">
              Integrity Status & Connected Nodes
            </h3>
          </div>
          <span className="text-[11px] font-mono text-emerald-600 font-medium">3 Nodes Online</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
          <div className="p-3.5 bg-stone-50 rounded-xl border border-stone-200/60 flex flex-col gap-1.5">
            <span className="text-stone-500 text-[11px]">Sync Cluster</span>
            <div className="flex items-center gap-2 text-stone-800 font-medium">
              <Laptop size={14} className="text-stone-500" />
              <Smartphone size={14} className="text-stone-500" />
              <Tablet size={14} className="text-stone-500" />
              <span className="text-xs font-mono">MacBook · iPhone · iPad</span>
            </div>
          </div>

          <div className="p-3.5 bg-stone-50 rounded-xl border border-stone-200/60 flex flex-col gap-1.5">
            <span className="text-stone-500 text-[11px]">Synced Volume</span>
            <div className="flex items-center justify-between">
              <span className="text-xs font-mono font-semibold text-stone-900">
                {syncedCount} of {photos.length} files
              </span>
              <span className="text-[11px] font-mono text-stone-500">
                {formatBytes(syncedBytes)} / {formatBytes(totalBytes)}
              </span>
            </div>
          </div>

          <div className="p-3.5 bg-stone-50 rounded-xl border border-stone-200/60 flex flex-col gap-1.5">
            <span className="text-stone-500 text-[11px]">Unbacked Local Media</span>
            <span className={`text-xs font-mono font-semibold ${unsyncedCount > 0 ? "text-amber-600" : "text-emerald-600"}`}>
              {unsyncedCount === 0 ? "0 Pending Files" : `${unsyncedCount} Pending Upload`}
            </span>
          </div>
        </div>
      </div>

      {/* Sync Execution Section */}
      <div className="bg-white border border-stone-200 rounded-2xl p-5 shadow-xs">
        {isSyncing ? (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between text-xs font-mono text-stone-800">
              <span className="flex items-center gap-2">
                <RefreshCw size={13} className="text-stone-900 animate-spin" />
                Synchronizing to {syncActiveCloud}...
              </span>
              <span>
                {syncCurrent} / {syncTotal} ({Math.round(syncProgress * 100)}%)
              </span>
            </div>
            <div className="w-full h-2 bg-stone-100 rounded-full overflow-hidden border border-stone-200">
              <div
                className="h-full bg-stone-900 transition-all duration-300"
                style={{ width: `${syncProgress * 100}%` }}
              />
            </div>
            <p className="text-[11px] text-stone-500">
              Transmitting encrypted photo byte buffers with non-destructive version metadata.
            </p>
          </div>
        ) : unsyncedCount > 0 ? (
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <span className="text-xs font-semibold text-stone-900 block">
                Pending Local Media Detected
              </span>
              <p className="text-xs text-stone-500 mt-0.5 leading-relaxed">
                You have {unsyncedCount} unique media captures awaiting synchronization with {syncActiveCloud}.
              </p>
            </div>
            <button
              onClick={onStartSync}
              className="flex items-center justify-center gap-2 bg-stone-900 hover:bg-stone-800 text-white font-medium text-xs px-5 py-3 rounded-xl transition cursor-pointer shadow-xs shrink-0"
            >
              <RefreshCw size={14} />
              <span>Sync {unsyncedCount} Files</span>
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3 bg-emerald-50/50 p-4 rounded-xl border border-emerald-200">
            <CheckCircle size={18} className="text-emerald-600 shrink-0" />
            <div>
              <h4 className="text-xs font-semibold text-emerald-900">All Media Vaults Synchronized</h4>
              <p className="text-[11px] text-emerald-700/90 mt-0.5">
                Target repository matches local cryptographic hash signatures. 0 deduplication conflicts detected.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
