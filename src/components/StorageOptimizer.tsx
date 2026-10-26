import React, { useMemo, useState } from "react";
import { HardDrive, PiggyBank, ShieldCheck, CheckCircle2, AlertTriangle, Play, FileCheck, ArrowUpRight } from "lucide-react";
import { PhotoEntity } from "../types";

interface StorageOptimizerProps {
  photos: PhotoEntity[];
  onSimulateImport: (params: {
    resourceName: string;
    title: string;
    sizeBytes: number;
    mimeType?: string;
    durationSeconds?: number;
  }) => void;
  importResult: {
    status: "duplicate" | "success" | null;
    photo?: PhotoEntity;
    duplicateOf?: PhotoEntity;
  } | null;
  onClearImportResult: () => void;
}

export function StorageOptimizer({
  photos,
  onSimulateImport,
  importResult,
  onClearImportResult
}: StorageOptimizerProps) {
  // Compute storage footprints
  const totalStorage = useMemo(() => {
    return photos.reduce((sum, p) => sum + p.sizeBytes, 0);
  }, [photos]);

  const savedStorage = useMemo(() => {
    // Non-destructive editing delta saves ~2MB of physical copies per applied filter!
    return photos.filter((p) => p.currentFilter !== null).length * 2000000;
  }, [photos]);

  const formatBytes = (bytes: number) => {
    if (bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  };

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Overview Intro */}
      <div>
        <h2 className="text-xl font-bold text-gray-900">Aura Storage Optimizer</h2>
        <p className="text-sm text-gray-500 mt-1">
          Monitor your digital footprint, audit active filters savings, and test our automatic duplicate blocker.
        </p>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Footprint */}
        <div className="bg-white border border-gray-150 rounded-2xl p-5 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-brand-purple/10 text-brand-purple flex items-center justify-center shrink-0">
            <HardDrive size={22} />
          </div>
          <div>
            <span className="text-[10px] text-gray-400 font-bold uppercase tracking-wider block">
              Active Storage Footprint
            </span>
            <span className="text-xl font-extrabold text-gray-900 font-mono mt-0.5 block">
              {formatBytes(totalStorage)}
            </span>
            <span className="text-xs text-gray-500 block mt-0.5">
              Across {photos.length} files in active directory
            </span>
          </div>
        </div>

        {/* Savings */}
        <div className="bg-brand-purple text-white rounded-2xl p-5 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-white/20 text-brand-purple-container flex items-center justify-center shrink-0">
            <PiggyBank size={22} />
          </div>
          <div>
            <span className="text-[10px] text-brand-purple-container font-bold uppercase tracking-wider block">
              AI Space Saved Via Delta History
            </span>
            <span className="text-xl font-extrabold text-white font-mono mt-0.5 block">
              {formatBytes(savedStorage)}
            </span>
            <span className="text-xs text-brand-purple-container block mt-0.5">
              Saved by preventing filter replication copies
            </span>
          </div>
        </div>
      </div>

      {/* Deduplication Logic Explanation */}
      <div className="bg-white border border-gray-150 rounded-2xl p-5 shadow-sm flex flex-col gap-3">
        <div className="flex items-center gap-2 border-b border-gray-150 pb-2.5">
          <ShieldCheck size={16} className="text-brand-purple" />
          <h3 className="text-sm font-bold text-gray-900">Deduplication Engine Intelligence</h3>
        </div>
        <p className="text-xs text-gray-600 leading-relaxed">
          The engine computes cryptographic **originalContentHash** signatures for incoming media packets. If an incoming file matches an existing signature exactly, Aura blocks it automatically, avoiding storage waste and media feed clutter.
        </p>
      </div>

      {/* Simulator Panel */}
      <div className="bg-white border border-gray-150 rounded-2xl p-5 shadow-sm flex flex-col gap-4">
        <div>
          <h3 className="text-sm font-bold text-gray-900">Duplicate Block Simulator Panel</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            Manually trigger simulations of duplicates and unique uploads to verify the active protection system.
          </p>
        </div>

        <div className="flex flex-col gap-2.5">
          <button
            onClick={() =>
              onSimulateImport({
                resourceName: "img_face_alice",
                title: "Alice Alternate Name.jpg",
                sizeBytes: 2400000
              })
            }
            className="flex items-center justify-between p-3.5 bg-red-50 hover:bg-red-100/70 text-red-800 border border-red-200 rounded-xl text-left cursor-pointer transition text-xs font-semibold"
          >
            <span>Simulate Upload: Duplicate Alice Portrait (Expected: BLOCKED)</span>
            <ArrowUpRight size={14} />
          </button>

          <button
            onClick={() =>
              onSimulateImport({
                resourceName: "img_scenic_mountain",
                title: "Wallpaper Copy.jpg",
                sizeBytes: 4500000
              })
            }
            className="flex items-center justify-between p-3.5 bg-red-50 hover:bg-red-100/70 text-red-800 border border-red-200 rounded-xl text-left cursor-pointer transition text-xs font-semibold"
          >
            <span>Simulate Upload: Duplicate Mountain Sunset (Expected: BLOCKED)</span>
            <ArrowUpRight size={14} />
          </button>

          <button
            onClick={() => {
              const id = Date.now();
              onSimulateImport({
                resourceName: "img_scenic_lake",
                title: `Unique Forest Lake ${id}.jpg`,
                sizeBytes: 3450000
              });
            }}
            className="flex items-center justify-between p-3.5 bg-brand-purple/10 hover:bg-brand-purple/15 text-brand-purple border border-brand-purple/20 rounded-xl text-left cursor-pointer transition text-xs font-semibold"
          >
            <span>Simulate Upload: Unique Lake Landscape (Expected: ALLOWED)</span>
            <ArrowUpRight size={14} />
          </button>

          <button
            onClick={() => {
              const id = Date.now();
              onSimulateImport({
                resourceName: "img_scenic_mountain",
                title: `Simulated Vlog Clip ${id}.mp4`,
                sizeBytes: 19500000,
                mimeType: "video/mp4",
                durationSeconds: 30
              });
            }}
            className="flex items-center justify-between p-3.5 bg-gray-50 hover:bg-gray-100/70 text-gray-700 border border-gray-200 rounded-xl text-left cursor-pointer transition text-xs font-semibold"
          >
            <span>Simulate Upload: Unique Video Clip (Expected: ALLOWED)</span>
            <ArrowUpRight size={14} />
          </button>

          <button
            onClick={() => {
              onSimulateImport({
                resourceName: "img_scenic_mountain",
                title: "Vlog Clip Copy.mp4",
                sizeBytes: 19500000,
                mimeType: "video/mp4",
                durationSeconds: 30
              });
            }}
            className="flex items-center justify-between p-3.5 bg-red-50 hover:bg-red-100/70 text-red-800 border border-red-200 rounded-xl text-left cursor-pointer transition text-xs font-semibold"
          >
            <span>Simulate Upload: Duplicate Video Clip (Expected: BLOCKED)</span>
            <ArrowUpRight size={14} />
          </button>
        </div>
      </div>

      {/* Simulation Feedback Dialogs */}
      {importResult && importResult.status === "duplicate" && importResult.duplicateOf && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-3xl p-6 border border-red-100 shadow-2xl max-w-sm w-full flex flex-col gap-4 text-center">
            <div className="w-12 h-12 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto mb-2">
              <AlertTriangle size={24} />
            </div>
            <div>
              <h4 className="text-base font-bold text-red-900">Duplicate Media Blocked</h4>
              <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                A file with the identical original Content Hash signature has been detected in your active media directory.
              </p>
            </div>

            <div className="bg-gray-50 p-3 rounded-xl border border-gray-150 text-left font-mono text-[10px] text-gray-600 flex flex-col gap-1.5">
              <div className="truncate">
                <strong className="text-gray-900">Name:</strong> {importResult.duplicateOf.title}
              </div>
              <div>
                <strong className="text-gray-900">Size:</strong> {formatBytes(importResult.duplicateOf.sizeBytes)}
              </div>
              <div className="truncate">
                <strong className="text-gray-900">MD5:</strong> {importResult.duplicateOf.originalContentHash}
              </div>
            </div>

            <p className="text-xs font-bold text-brand-purple">
              Duplicate blocked to save {formatBytes(importResult.duplicateOf.sizeBytes)} of local storage!
            </p>

            <button
              onClick={onClearImportResult}
              className="w-full bg-brand-purple text-white font-semibold py-2.5 rounded-xl text-xs hover:bg-brand-purple/95 transition cursor-pointer"
            >
              Awesome, Thanks!
            </button>
          </div>
        </div>
      )}

      {importResult && importResult.status === "success" && importResult.photo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-3xl p-6 border border-green-100 shadow-2xl max-w-sm w-full flex flex-col gap-4 text-center">
            <div className="w-12 h-12 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-2">
              <CheckCircle2 size={24} />
            </div>
            <div>
              <h4 className="text-base font-bold text-green-900">Import Successful</h4>
              <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                The file signature is unique. It has been analyzed and successfully appended to your gallery feed.
              </p>
            </div>

            <div className="bg-gray-50 p-3 rounded-xl border border-gray-150 text-left font-mono text-[10px] text-gray-600 flex flex-col gap-1.5">
              <div className="truncate">
                <strong className="text-gray-900">Name:</strong> {importResult.photo.title}
              </div>
              <div>
                <strong className="text-gray-900">Type:</strong> {importResult.photo.mimeType}
              </div>
              <div className="truncate">
                <strong className="text-gray-900">Signature:</strong> {importResult.photo.originalContentHash}
              </div>
            </div>

            <button
              onClick={onClearImportResult}
              className="w-full bg-brand-purple text-white font-semibold py-2.5 rounded-xl text-xs hover:bg-brand-purple/95 transition cursor-pointer"
            >
              Great
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
