import React, { useMemo } from "react";
import {
  HardDrive,
  PiggyBank,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  ArrowUpRight,
  Database,
  Film,
  Image as ImageIcon
} from "lucide-react";
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

  const imageStorage = useMemo(() => {
    return photos.filter((p) => p.durationSeconds === 0).reduce((sum, p) => sum + p.sizeBytes, 0);
  }, [photos]);

  const videoStorage = useMemo(() => {
    return photos.filter((p) => p.durationSeconds > 0).reduce((sum, p) => sum + p.sizeBytes, 0);
  }, [photos]);

  // Delta version savings: non-destructive edits save ~2.4MB per modification
  const savedStorage = useMemo(() => {
    return photos.filter((p) => p.currentFilter !== null || (p.adjustments && Object.values(p.adjustments).some((v) => v !== 0))).length * 2400000;
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
        <h2 className="text-xl font-bold text-stone-900 tracking-tight">Storage & Deduplication Optimizer</h2>
        <p className="text-xs text-stone-500 mt-1">
          Cryptographic hash validation prevents identical media re-imports, while parametric deltas eliminate redundant physical copy copies.
        </p>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Footprint */}
        <div className="bg-white border border-stone-200 rounded-2xl p-5 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-stone-100 text-stone-800 flex items-center justify-center shrink-0 border border-stone-200">
            <HardDrive size={22} />
          </div>
          <div>
            <span className="text-[10px] text-stone-500 font-mono uppercase tracking-wider block">
              Active Library Footprint
            </span>
            <span className="text-xl font-bold text-stone-900 font-mono mt-0.5 block">
              {formatBytes(totalStorage)}
            </span>
            <span className="text-[11px] text-stone-500 block mt-0.5 font-mono">
              {photos.length} files ({formatBytes(imageStorage)} stills · {formatBytes(videoStorage)} video)
            </span>
          </div>
        </div>

        {/* Savings */}
        <div className="bg-stone-900 text-white border border-stone-800 rounded-2xl p-5 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-stone-800 text-stone-200 flex items-center justify-center shrink-0">
            <PiggyBank size={22} />
          </div>
          <div>
            <span className="text-[10px] text-stone-400 font-mono uppercase tracking-wider block">
              Parametric Space Conserved
            </span>
            <span className="text-xl font-bold text-white font-mono mt-0.5 block">
              {formatBytes(savedStorage)}
            </span>
            <span className="text-[11px] text-stone-400 block mt-0.5">
              Saved by non-destructive adjustment history
            </span>
          </div>
        </div>
      </div>

      {/* Deduplication Engine Breakdown */}
      <div className="bg-white border border-stone-200 rounded-2xl p-5 shadow-xs flex flex-col gap-3">
        <div className="flex items-center gap-2 border-b border-stone-200 pb-2.5">
          <ShieldCheck size={16} className="text-stone-700" />
          <h3 className="text-xs font-semibold uppercase tracking-wider text-stone-900">
            Cryptographic Deduplication Architecture
          </h3>
        </div>
        <p className="text-xs text-stone-600 leading-relaxed">
          The ingestion pipeline computes an immutable <strong>originalContentHash</strong> signature for each candidate file. If an identical hash is already present in the library, the upload is rejected at ingress, preventing wasted disk allocation, duplicate cloud backups, and media feed clutter.
        </p>
      </div>

      {/* Simulator Test Bench */}
      <div className="bg-white border border-stone-200 rounded-2xl p-5 shadow-xs flex flex-col gap-4">
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-stone-900">
            Deduplication Verification Test Bench
          </h3>
          <p className="text-xs text-stone-500 mt-0.5">
            Trigger simulated upload payloads to test real-time duplicate rejection and unique file ingestion.
          </p>
        </div>

        <div className="flex flex-col gap-2.5">
          <button
            onClick={() =>
              onSimulateImport({
                resourceName: "img_face_alice",
                title: "Alice Copy Test.jpg",
                sizeBytes: 2400000
              })
            }
            className="flex items-center justify-between p-3.5 bg-rose-50/70 hover:bg-rose-100/70 text-rose-900 border border-rose-200 rounded-xl text-left cursor-pointer transition text-xs font-medium"
          >
            <span>Simulate Upload: Duplicate Alice Portrait (Expected: BLOCKED)</span>
            <ArrowUpRight size={14} className="text-rose-500 shrink-0" />
          </button>

          <button
            onClick={() =>
              onSimulateImport({
                resourceName: "img_scenic_mountain",
                title: "Mountain Sunset Copy.jpg",
                sizeBytes: 4500000
              })
            }
            className="flex items-center justify-between p-3.5 bg-rose-50/70 hover:bg-rose-100/70 text-rose-900 border border-rose-200 rounded-xl text-left cursor-pointer transition text-xs font-medium"
          >
            <span>Simulate Upload: Duplicate Mountain Sunset (Expected: BLOCKED)</span>
            <ArrowUpRight size={14} className="text-rose-500 shrink-0" />
          </button>

          <button
            onClick={() => {
              const id = Date.now();
              onSimulateImport({
                resourceName: "img_scenic_lake",
                title: `Alpine Lake Raw ${id}.jpg`,
                sizeBytes: 3950000
              });
            }}
            className="flex items-center justify-between p-3.5 bg-stone-50 hover:bg-stone-100 text-stone-800 border border-stone-200 rounded-xl text-left cursor-pointer transition text-xs font-medium"
          >
            <span>Simulate Upload: Unique Alpine Lake (Expected: ALLOWED)</span>
            <ArrowUpRight size={14} className="text-stone-500 shrink-0" />
          </button>

          <button
            onClick={() => {
              const id = Date.now();
              onSimulateImport({
                resourceName: "img_face_bob",
                title: `Studio Reel ${id}.mp4`,
                sizeBytes: 21500000,
                mimeType: "video/mp4",
                durationSeconds: 32
              });
            }}
            className="flex items-center justify-between p-3.5 bg-stone-50 hover:bg-stone-100 text-stone-800 border border-stone-200 rounded-xl text-left cursor-pointer transition text-xs font-medium"
          >
            <span>Simulate Upload: Unique Studio Video (Expected: ALLOWED)</span>
            <ArrowUpRight size={14} className="text-stone-500 shrink-0" />
          </button>
        </div>
      </div>

      {/* Simulation Feedback Dialogs */}
      {importResult && importResult.status === "duplicate" && importResult.duplicateOf && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl p-6 border border-rose-200 shadow-2xl max-w-sm w-full flex flex-col gap-4 text-center">
            <div className="w-12 h-12 bg-rose-100 text-rose-600 rounded-full flex items-center justify-center mx-auto">
              <AlertTriangle size={24} />
            </div>
            <div>
              <h4 className="text-sm font-bold text-stone-900">Duplicate Media Blocked</h4>
              <p className="text-xs text-stone-500 mt-1 leading-relaxed">
                An identical cryptographic hash already exists in your active library catalogue.
              </p>
            </div>

            <div className="bg-stone-50 p-3 rounded-xl border border-stone-200 text-left font-mono text-[11px] text-stone-600 flex flex-col gap-1.5">
              <div className="truncate">
                <strong className="text-stone-900">Original:</strong> {importResult.duplicateOf.title}
              </div>
              <div>
                <strong className="text-stone-900">Size:</strong> {formatBytes(importResult.duplicateOf.sizeBytes)}
              </div>
              <div className="truncate">
                <strong className="text-stone-900">Hash:</strong> {importResult.duplicateOf.originalContentHash}
              </div>
            </div>

            <button
              onClick={onClearImportResult}
              className="w-full bg-stone-900 text-white font-medium py-2.5 rounded-xl text-xs hover:bg-stone-800 transition cursor-pointer"
            >
              Acknowledge
            </button>
          </div>
        </div>
      )}

      {importResult && importResult.status === "success" && importResult.photo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl p-6 border border-emerald-200 shadow-2xl max-w-sm w-full flex flex-col gap-4 text-center">
            <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 size={24} />
            </div>
            <div>
              <h4 className="text-sm font-bold text-stone-900">Unique Media Ingested</h4>
              <p className="text-xs text-stone-500 mt-1 leading-relaxed">
                Hash verified unique. New media entity initialized in gallery catalogue.
              </p>
            </div>

            <div className="bg-stone-50 p-3 rounded-xl border border-stone-200 text-left font-mono text-[11px] text-stone-600 flex flex-col gap-1.5">
              <div className="truncate">
                <strong className="text-stone-900">Title:</strong> {importResult.photo.title}
              </div>
              <div>
                <strong className="text-stone-900">Format:</strong> {importResult.photo.mimeType}
              </div>
              <div className="truncate">
                <strong className="text-stone-900">Hash:</strong> {importResult.photo.originalContentHash}
              </div>
            </div>

            <button
              onClick={onClearImportResult}
              className="w-full bg-stone-900 text-white font-medium py-2.5 rounded-xl text-xs hover:bg-stone-800 transition cursor-pointer"
            >
              Continue
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
