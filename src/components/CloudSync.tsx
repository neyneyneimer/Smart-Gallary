import React, { useMemo } from "react";
import { Cloud, CloudLightning, RefreshCw, CheckCircle, Shield, Tablet, Smartphone, Laptop } from "lucide-react";
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

  const providers = [
    { name: "AuraCloud", label: "AuraCloud Storage" },
    { name: "Google Photos", label: "Google Photos Storage" },
    { name: "OneDrive", label: "OneDrive Storage" },
    { name: "Dropbox", label: "Dropbox Storage" }
  ];

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Intro Header */}
      <div>
        <h2 className="text-xl font-bold text-gray-900">Multi-Device Backup Sync</h2>
        <p className="text-sm text-gray-500 mt-1">
          Securely synchronise your unique media footprint across devices while maintaining absolute local duplicates protection.
        </p>
      </div>

      {/* Connected Cloud Services Grid */}
      <div className="flex flex-col gap-3">
        <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider pl-1">
          Select Active Target Backup Provider
        </h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {providers.map((provider) => {
            const isSelected = syncActiveCloud === provider.label;
            return (
              <button
                key={provider.name}
                onClick={() => onChangeCloud(provider.label)}
                className={`flex flex-col items-center justify-center p-4 rounded-2xl border transition cursor-pointer text-center ${
                  isSelected
                    ? "bg-brand-purple/10 border-brand-purple text-brand-purple shadow-sm"
                    : "bg-white border-gray-150 hover:bg-gray-50 text-gray-600"
                }`}
              >
                <Cloud size={24} className={isSelected ? "text-brand-purple" : "text-gray-400"} />
                <span className="text-xs font-bold mt-2 truncate max-w-full">
                  {provider.name}
                </span>
                <span className="text-[9px] text-gray-400 mt-0.5">
                  {isSelected ? "Active Client" : "Click to select"}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Sync Status Grid Card */}
      <div className="bg-white border border-gray-150 rounded-2xl p-5 shadow-sm flex flex-col gap-4">
        <div className="flex items-center gap-2 border-b border-gray-150 pb-3">
          <Shield size={16} className="text-brand-purple" />
          <h3 className="text-sm font-bold text-gray-900">Security & Backup Integrity State</h3>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-medium">
          <div className="flex items-center justify-between p-3.5 bg-gray-50 rounded-xl border border-gray-100">
            <span className="text-gray-500">Active Sync Nodes</span>
            <div className="flex items-center gap-1 text-gray-800 font-bold">
              <Laptop size={12} className="text-gray-500" />
              <Smartphone size={12} className="text-gray-500" />
              <Tablet size={12} className="text-gray-500" />
              <span>3 Nodes Connected</span>
            </div>
          </div>

          <div className="flex items-center justify-between p-3.5 bg-gray-50 rounded-xl border border-gray-100">
            <span className="text-gray-500">Synced to cloud</span>
            <span className="text-green-600 font-bold font-mono">{syncedCount} files synced</span>
          </div>

          <div className="flex items-center justify-between p-3.5 bg-gray-50 rounded-xl border border-gray-100">
            <span className="text-gray-500">Unbacked local media</span>
            <span className={`font-bold font-mono ${unsyncedCount > 0 ? "text-amber-500" : "text-green-600"}`}>
              {unsyncedCount} unbacked files
            </span>
          </div>
        </div>
      </div>

      {/* Action Sync Dashboard Card */}
      <div className="bg-white border border-gray-150 rounded-2xl p-5 shadow-sm">
        {isSyncing ? (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between text-xs font-bold text-gray-800">
              <span className="flex items-center gap-1.5 animate-spin">
                <RefreshCw size={13} className="text-brand-purple" />
                Backup active on {syncActiveCloud}...
              </span>
              <span className="font-mono">
                {syncCurrent} / {syncTotal} files ({Math.round(syncProgress * 100)}%)
              </span>
            </div>
            <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden border border-gray-100">
              <div
                className="h-full bg-brand-purple transition-all duration-300"
                style={{ width: `${syncProgress * 100}%` }}
              />
            </div>
            <p className="text-[10px] text-gray-500 leading-relaxed mt-1">
              Transferring media packets non-destructively. Duplicate prevention checking actively running in background cloud services.
            </p>
          </div>
        ) : unsyncedCount > 0 ? (
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <span className="text-xs font-bold text-amber-500 block">
                Pending Backup Required
              </span>
              <p className="text-xs text-gray-500 mt-0.5 leading-relaxed">
                You have {unsyncedCount} local unique files that are not synchronized with {syncActiveCloud}.
              </p>
            </div>
            <button
              onClick={onStartSync}
              className="flex items-center justify-center gap-2 bg-brand-purple hover:bg-brand-purple/95 text-white font-semibold text-xs px-5 py-3 rounded-xl transition shadow hover:shadow-brand-purple/20 cursor-pointer shrink-0"
            >
              <RefreshCw size={14} />
              Sync Unbacked Media ({unsyncedCount})
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3 bg-green-50/50 p-4 rounded-xl border border-green-200">
            <CheckCircle size={20} className="text-green-600 shrink-0" />
            <div>
              <h4 className="text-xs font-bold text-green-800">All media backed up safely</h4>
              <p className="text-[10px] text-green-600/80 mt-0.5 leading-relaxed">
                Synchronization up to date across all 3 active device nodes. No duplicate files or bloated redundancies found.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
