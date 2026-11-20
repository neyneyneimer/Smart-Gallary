import React, { useState, useMemo } from "react";
import {
  Grid,
  List,
  Search,
  ArrowUpDown,
  ChevronLeft,
  Play,
  Image as ImageIcon,
  FolderOpen,
  Heart,
  CheckSquare,
  Square,
  Cloud,
  Trash2,
  SlidersHorizontal
} from "lucide-react";
import { PhotoEntity } from "../types";

interface GalleryFeedProps {
  photos: PhotoEntity[];
  selectedFaceAlbum: string | null;
  onExitFaceAlbum: () => void;
  onPhotoSelect: (photo: PhotoEntity) => void;
  onToggleFavorite: (id: number) => void;
  onBatchSync: (ids: number[]) => void;
  onBatchDelete: (ids: number[]) => void;
}

type MediaFilter = "ALL" | "PHOTOS" | "VIDEOS" | "FAVORITES" | "UNSYNCED";
type GroupingMode = "NONE" | "SCENES" | "ATTIRE" | "ENVIRONMENT" | "LIGHTING";
type SortOption = "date_desc" | "date_asc" | "size_desc" | "size_asc";

export function GalleryFeed({
  photos,
  selectedFaceAlbum,
  onExitFaceAlbum,
  onPhotoSelect,
  onToggleFavorite,
  onBatchSync,
  onBatchDelete
}: GalleryFeedProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [mediaFilter, setMediaFilter] = useState<MediaFilter>("ALL");
  const [groupingMode, setGroupingMode] = useState<GroupingMode>("NONE");
  const [sortOption, setSortOption] = useState<SortOption>("date_desc");
  const [viewLayout, setViewLayout] = useState<"grid" | "list">("grid");

  // Multi-select batch mode state
  const [isSelectMode, setIsSelectMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // 1. Filter by face album if active
  const albumPhotos = useMemo(() => {
    if (!selectedFaceAlbum) return photos;
    return photos.filter((p) => p.faceTag === selectedFaceAlbum);
  }, [photos, selectedFaceAlbum]);

  // 2. Filter by media type tabs
  const typeFiltered = useMemo(() => {
    return albumPhotos.filter((p) => {
      if (mediaFilter === "PHOTOS") return p.durationSeconds === 0;
      if (mediaFilter === "VIDEOS") return p.durationSeconds > 0;
      if (mediaFilter === "FAVORITES") return !!p.isFavorite;
      if (mediaFilter === "UNSYNCED") return !p.hasBackupSynced;
      return true;
    });
  }, [albumPhotos, mediaFilter]);

  // 3. Filter by search query (title, attire, environment, lighting, faceTag)
  const filteredPhotos = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return typeFiltered;
    return typeFiltered.filter((p) => {
      const matchTitle = p.title.toLowerCase().includes(q);
      const matchFace = (p.faceTag || "").toLowerCase().includes(q);
      const matchAttire = (p.attire || "").toLowerCase().includes(q);
      const matchEnv = (p.environment || "").toLowerCase().includes(q);
      const matchLight = (p.lighting || "").toLowerCase().includes(q);
      const matchCamera = (p.cameraModel || "").toLowerCase().includes(q);
      return matchTitle || matchFace || matchAttire || matchEnv || matchLight || matchCamera;
    });
  }, [typeFiltered, searchQuery]);

  // 4. Sort photos
  const sortedPhotos = useMemo(() => {
    return [...filteredPhotos].sort((a, b) => {
      if (sortOption === "date_desc") return b.dateTaken - a.dateTaken;
      if (sortOption === "date_asc") return a.dateTaken - b.dateTaken;
      if (sortOption === "size_desc") return b.sizeBytes - a.sizeBytes;
      if (sortOption === "size_asc") return a.sizeBytes - b.sizeBytes;
      return 0;
    });
  }, [filteredPhotos, sortOption]);

  // 5. Group photos into nested folders if groupingMode is not NONE
  const groupedSections = useMemo(() => {
    if (groupingMode === "NONE") return null;

    const groups: { [key: string]: PhotoEntity[] } = {};

    sortedPhotos.forEach((photo) => {
      let key = "General Collection";
      if (groupingMode === "SCENES") {
        key = photo.environment || "Landscapes & Nature";
      } else if (groupingMode === "ATTIRE") {
        key = photo.attire || "Casual & Everyday";
      } else if (groupingMode === "ENVIRONMENT") {
        key = photo.environment || "Scenic Outdoors";
      } else if (groupingMode === "LIGHTING") {
        key = photo.lighting || "Natural Ambient Light";
      }

      if (!groups[key]) groups[key] = [];
      groups[key].push(photo);
    });

    return Object.entries(groups).sort((a, b) => a[0].localeCompare(b[0]));
  }, [sortedPhotos, groupingMode]);

  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSelectAll = () => {
    if (selectedIds.size === sortedPhotos.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(sortedPhotos.map((p) => p.id)));
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  };

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = Math.floor(sec % 60);
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  const getPresetCSS = (filterName: string | null) => {
    switch (filterName) {
      case "Vintage":
        return "sepia(0.5) brightness(0.95) contrast(1.1) saturate(1.2)";
      case "Mono":
        return "grayscale(1) contrast(1.25) brightness(0.95)";
      case "Warm Glow":
        return "sepia(0.25) brightness(1.08) saturate(1.25) contrast(1.05)";
      case "Cool Mist":
        return "contrast(0.95) saturate(0.85) brightness(1.05) hue-rotate(15deg)";
      case "Cyberpunk":
        return "hue-rotate(60deg) saturate(1.9) brightness(1.05) contrast(1.15)";
      default:
        return "none";
    }
  };

  const renderPhotoItem = (photo: PhotoEntity) => {
    const isVid = photo.durationSeconds > 0;
    const isSelected = selectedIds.has(photo.id);

    if (viewLayout === "list") {
      return (
        <div
          key={photo.id}
          onClick={() => {
            if (isSelectMode) toggleSelect(photo.id);
            else onPhotoSelect(photo);
          }}
          className={`flex items-center gap-4 bg-white border rounded-xl p-3 cursor-pointer hover:bg-stone-50 transition ${
            isSelected ? "border-stone-900 bg-stone-50" : "border-stone-200"
          }`}
        >
          {isSelectMode && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                toggleSelect(photo.id);
              }}
              className="text-stone-900"
            >
              {isSelected ? <CheckSquare size={18} /> : <Square size={18} className="text-stone-400" />}
            </button>
          )}

          <div className="relative w-16 h-16 rounded-lg overflow-hidden border border-stone-200 shrink-0 bg-stone-100">
            <img
              src={`/images/${photo.resourceName}.jpg`}
              alt={photo.title}
              referrerPolicy="no-referrer"
              style={{ filter: getPresetCSS(photo.currentFilter) }}
              className="w-full h-full object-cover"
            />
            {isVid && (
              <div className="absolute inset-0 bg-black/30 flex items-center justify-center">
                <Play size={14} fill="white" className="text-white" />
              </div>
            )}
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-semibold text-stone-900 truncate">{photo.title}</h4>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleFavorite(photo.id);
                }}
                className={`p-1 transition ${
                  photo.isFavorite ? "text-rose-500" : "text-stone-300 hover:text-stone-500"
                }`}
              >
                <Heart size={15} fill={photo.isFavorite ? "currentColor" : "none"} />
              </button>
            </div>

            {/* Zero-Pill Typographic Metadata */}
            <div className="flex items-center gap-1.5 text-[11px] text-stone-500 mt-1 font-mono">
              <span>{new Date(photo.dateTaken).toLocaleDateString()}</span>
              <span>·</span>
              <span>{formatBytes(photo.sizeBytes)}</span>
              {isVid && (
                <>
                  <span>·</span>
                  <span>video ({formatDuration(photo.durationSeconds)})</span>
                </>
              )}
              {photo.faceTag && (
                <>
                  <span>·</span>
                  <span className="text-stone-700 font-sans font-medium">{photo.faceTag}</span>
                </>
              )}
            </div>
          </div>
        </div>
      );
    }

    // Grid Layout
    return (
      <div
        key={photo.id}
        onClick={() => {
          if (isSelectMode) toggleSelect(photo.id);
          else onPhotoSelect(photo);
        }}
        className={`group relative bg-white border rounded-xl overflow-hidden cursor-pointer transition-all duration-200 hover:shadow-md ${
          isSelected ? "border-stone-900 ring-2 ring-stone-900" : "border-stone-200"
        }`}
      >
        <div className="relative aspect-square w-full bg-stone-100 overflow-hidden">
          <img
            src={`/images/${photo.resourceName}.jpg`}
            alt={photo.title}
            referrerPolicy="no-referrer"
            style={{ filter: getPresetCSS(photo.currentFilter) }}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />

          {/* Quick Favorite Button */}
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleFavorite(photo.id);
            }}
            className={`absolute top-2.5 right-2.5 p-1.5 rounded-full backdrop-blur-md transition z-10 ${
              photo.isFavorite
                ? "bg-rose-500 text-white"
                : "bg-black/40 text-white/80 hover:text-white opacity-0 group-hover:opacity-100"
            }`}
          >
            <Heart size={13} fill={photo.isFavorite ? "currentColor" : "none"} />
          </button>

          {/* Select Mode Checkbox */}
          {isSelectMode && (
            <div className="absolute top-2.5 left-2.5 z-10">
              <div
                className={`p-1 rounded-md backdrop-blur-md ${
                  isSelected ? "bg-stone-900 text-white" : "bg-black/40 text-white"
                }`}
              >
                {isSelected ? <CheckSquare size={16} /> : <Square size={16} />}
              </div>
            </div>
          )}

          {/* Media Indicators */}
          {!isSelectMode && (
            <div className="absolute top-2.5 left-2.5 flex items-center justify-center w-6 h-6 rounded-md bg-black/40 backdrop-blur-md text-white">
              {isVid ? <Play size={11} fill="white" /> : <ImageIcon size={11} />}
            </div>
          )}

          {isVid && (
            <span className="absolute bottom-2.5 right-2.5 text-[10px] font-mono font-medium text-white bg-black/60 px-1.5 py-0.5 rounded backdrop-blur-xs">
              {formatDuration(photo.durationSeconds)}
            </span>
          )}
        </div>

        {/* Card Metadata Footer (Zero-Pill formatting) */}
        <div className="p-3">
          <h4 className="text-xs font-semibold text-stone-900 truncate">{photo.title}</h4>
          <div className="flex items-center justify-between text-[10px] font-mono text-stone-500 mt-1">
            <span>{new Date(photo.dateTaken).toLocaleDateString()}</span>
            <span>{formatBytes(photo.sizeBytes)}</span>
          </div>
          {photo.faceTag && (
            <span className="text-[10px] text-stone-600 block mt-1 truncate">
              {photo.faceTag} {photo.environment ? `· ${photo.environment}` : ""}
            </span>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Sub-Album Active Header */}
      {selectedFaceAlbum && (
        <div className="bg-stone-100 p-4 rounded-2xl border border-stone-200 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={onExitFaceAlbum}
              className="p-2 text-stone-700 bg-white rounded-xl shadow-xs border border-stone-200 hover:bg-stone-50 transition cursor-pointer"
            >
              <ChevronLeft size={16} />
            </button>
            <div>
              <span className="text-[10px] uppercase font-semibold tracking-wider text-stone-500">
                Filtered Album
              </span>
              <h2 className="text-lg font-bold text-stone-900">{selectedFaceAlbum}</h2>
            </div>
          </div>
          <div className="text-right">
            <span className="text-xs font-mono text-stone-500 block">
              {albumPhotos.length} {albumPhotos.length === 1 ? "item" : "items"}
            </span>
            <button
              onClick={onExitFaceAlbum}
              className="text-[11px] font-medium text-stone-900 hover:underline cursor-pointer"
            >
              All Photos
            </button>
          </div>
        </div>
      )}

      {/* Control Toolbar */}
      <div className="flex flex-col gap-3">
        {/* Search & Actions Bar */}
        <div className="bg-white rounded-2xl border border-stone-200 p-3 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Search Input */}
          <div className="relative flex-1">
            <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-stone-400">
              <Search size={15} />
            </span>
            <input
              type="text"
              placeholder="Search media, face tags, camera, lighting..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 border border-stone-200 rounded-xl text-xs focus:outline-none focus:border-stone-800 transition bg-stone-50/50"
            />
          </div>

          <div className="flex items-center gap-2">
            {/* Multi-Select Toggle */}
            <button
              onClick={() => {
                setIsSelectMode(!isSelectMode);
                setSelectedIds(new Set());
              }}
              className={`px-3 py-2 rounded-xl text-xs font-medium border transition cursor-pointer ${
                isSelectMode
                  ? "bg-stone-900 text-white border-stone-900"
                  : "bg-white text-stone-700 border-stone-200 hover:bg-stone-50"
              }`}
            >
              {isSelectMode ? "Cancel Select" : "Select"}
            </button>

            {/* Sorting Dropdown */}
            <div className="flex items-center gap-1.5 border border-stone-200 px-3 py-2 rounded-xl text-xs bg-white">
              <ArrowUpDown size={13} className="text-stone-500" />
              <select
                value={sortOption}
                onChange={(e) => setSortOption(e.target.value as SortOption)}
                className="bg-transparent font-medium text-stone-700 focus:outline-none cursor-pointer text-xs"
              >
                <option value="date_desc">Newest First</option>
                <option value="date_asc">Oldest First</option>
                <option value="size_desc">Largest Size</option>
                <option value="size_asc">Smallest Size</option>
              </select>
            </div>

            {/* Layout Toggle (Grid / List) */}
            <div className="flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200 shrink-0">
              <button
                onClick={() => setViewLayout("grid")}
                className={`p-1.5 rounded-lg transition ${
                  viewLayout === "grid" ? "bg-white text-stone-900 shadow-xs" : "text-stone-500 hover:text-stone-900"
                }`}
                title="Grid Layout"
              >
                <Grid size={15} />
              </button>
              <button
                onClick={() => setViewLayout("list")}
                className={`p-1.5 rounded-lg transition ${
                  viewLayout === "list" ? "bg-white text-stone-900 shadow-xs" : "text-stone-500 hover:text-stone-900"
                }`}
                title="List Layout"
              >
                <List size={15} />
              </button>
            </div>
          </div>
        </div>

        {/* Filter Tabs & Folder Grouping Row */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* Media Type Tabs */}
          <div className="flex items-center gap-1 p-1 bg-stone-100 rounded-xl border border-stone-200">
            {[
              { id: "ALL", label: "All Media" },
              { id: "PHOTOS", label: "Photos" },
              { id: "VIDEOS", label: "Videos" },
              { id: "FAVORITES", label: "Favorites" },
              { id: "UNSYNCED", label: "Unsynced" }
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setMediaFilter(tab.id as MediaFilter)}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg transition cursor-pointer ${
                  mediaFilter === tab.id
                    ? "bg-white text-stone-900 shadow-xs font-semibold"
                    : "text-stone-600 hover:text-stone-900"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Grouping Mode Dropdown */}
          <div className="flex items-center gap-2 text-xs text-stone-500">
            <span className="text-[11px] font-mono">Group:</span>
            <div className="flex items-center gap-1 bg-stone-100 p-1 rounded-xl border border-stone-200">
              {[
                { id: "NONE", label: "Flat" },
                { id: "SCENES", label: "Scenes" },
                { id: "ENVIRONMENT", label: "Environment" },
                { id: "LIGHTING", label: "Lighting" }
              ].map((mode) => (
                <button
                  key={mode.id}
                  onClick={() => setGroupingMode(mode.id as GroupingMode)}
                  className={`px-2.5 py-1 text-[11px] font-medium rounded-lg transition cursor-pointer ${
                    groupingMode === mode.id
                      ? "bg-white text-stone-900 shadow-xs font-semibold"
                      : "text-stone-600 hover:text-stone-900"
                  }`}
                >
                  {mode.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Main Media Stream */}
      {sortedPhotos.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-16 bg-white rounded-2xl border border-stone-200 text-center shadow-xs">
          <FolderOpen size={36} className="text-stone-300 mb-3" />
          <h3 className="text-sm font-semibold text-stone-700">No media found</h3>
          <p className="text-xs text-stone-500 mt-1 max-w-xs leading-relaxed">
            No items matched the current filter or search criteria.
          </p>
        </div>
      ) : groupingMode === "NONE" ? (
        <div
          className={
            viewLayout === "grid"
              ? "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4"
              : "flex flex-col gap-3"
          }
        >
          {sortedPhotos.map(renderPhotoItem)}
        </div>
      ) : (
        <div className="flex flex-col gap-8">
          {groupedSections?.map(([sectionTitle, sectionPhotos]) => (
            <div key={sectionTitle} className="flex flex-col gap-3">
              <div className="flex items-center gap-2 border-b border-stone-200 pb-2">
                <FolderOpen size={14} className="text-stone-500" />
                <h3 className="text-xs font-semibold text-stone-900 uppercase tracking-wider">
                  {sectionTitle}
                </h3>
                <span className="text-[10px] font-mono text-stone-500">
                  {sectionPhotos.length} {sectionPhotos.length === 1 ? "item" : "items"}
                </span>
              </div>

              <div
                className={
                  viewLayout === "grid"
                    ? "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4"
                    : "flex flex-col gap-3"
                }
              >
                {sectionPhotos.map(renderPhotoItem)}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Floating Bottom Batch Action Bar */}
      {isSelectMode && selectedIds.size > 0 && (
        <div className="fixed bottom-6 inset-x-0 z-40 flex justify-center px-4 pointer-events-none">
          <div className="bg-stone-900 text-white rounded-2xl shadow-xl px-5 py-3 border border-stone-800 flex items-center gap-4 pointer-events-auto">
            <span className="text-xs font-mono font-medium">
              {selectedIds.size} {selectedIds.size === 1 ? "item" : "items"} selected
            </span>

            <div className="h-4 w-px bg-stone-700" />

            <button
              onClick={() => onBatchSync(Array.from(selectedIds))}
              className="flex items-center gap-1.5 text-xs font-medium hover:text-stone-200 cursor-pointer"
            >
              <Cloud size={14} />
              <span>Sync to Cloud</span>
            </button>

            <button
              onClick={() => {
                onBatchDelete(Array.from(selectedIds));
                setSelectedIds(new Set());
              }}
              className="flex items-center gap-1.5 text-xs font-medium text-rose-400 hover:text-rose-300 cursor-pointer"
            >
              <Trash2 size={14} />
              <span>Delete</span>
            </button>

            <button
              onClick={() => setSelectedIds(new Set())}
              className="text-[11px] text-stone-400 hover:text-white cursor-pointer ml-1"
            >
              Clear
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
