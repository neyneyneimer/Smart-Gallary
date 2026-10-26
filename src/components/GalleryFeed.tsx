import React, { useState, useMemo } from "react";
import { Grid, List, Search, ArrowUpDown, ChevronLeft, Calendar, FileType, Play, Image as ImageIcon, FolderOpen } from "lucide-react";
import { PhotoEntity } from "../types";

interface GalleryFeedProps {
  photos: PhotoEntity[];
  selectedFaceAlbum: string | null;
  onExitFaceAlbum: () => void;
  onPhotoSelect: (photo: PhotoEntity) => void;
}

type GroupingMode = "NONE" | "SCENES" | "ATTIRE" | "ENVIRONMENT" | "LIGHTING";
type SortOption = "date_desc" | "date_asc" | "size_desc" | "size_asc";

export function GalleryFeed({
  photos,
  selectedFaceAlbum,
  onExitFaceAlbum,
  onPhotoSelect
}: GalleryFeedProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [groupingMode, setGroupingMode] = useState<GroupingMode>("NONE");
  const [sortOption, setSortOption] = useState<SortOption>("date_desc");
  const [viewLayout, setViewLayout] = useState<"grid" | "list">("grid");

  // 1. Filter by face album if active
  const albumPhotos = useMemo(() => {
    if (!selectedFaceAlbum) return photos;
    return photos.filter((p) => p.faceTag === selectedFaceAlbum);
  }, [photos, selectedFaceAlbum]);

  // 2. Filter by search query (title, attire, environment, lighting, description)
  const filteredPhotos = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return albumPhotos;
    return albumPhotos.filter((p) => {
      const matchTitle = p.title.toLowerCase().includes(q);
      const matchFace = (p.faceTag || "").toLowerCase().includes(q);
      const matchAttire = (p.attire || "").toLowerCase().includes(q);
      const matchEnv = (p.environment || "").toLowerCase().includes(q);
      const matchLight = (p.lighting || "").toLowerCase().includes(q);
      return matchTitle || matchFace || matchAttire || matchEnv || matchLight;
    });
  }, [albumPhotos, searchQuery]);

  // 3. Sort photos
  const sortedPhotos = useMemo(() => {
    return [...filteredPhotos].sort((a, b) => {
      if (sortOption === "date_desc") return b.dateTaken - a.dateTaken;
      if (sortOption === "date_asc") return a.dateTaken - b.dateTaken;
      if (sortOption === "size_desc") return b.sizeBytes - a.sizeBytes;
      if (sortOption === "size_asc") return a.sizeBytes - b.sizeBytes;
      return 0;
    });
  }, [filteredPhotos, sortOption]);

  // 4. Group photos into nested folders if groupingMode is not NONE
  const groupedSections = useMemo(() => {
    if (groupingMode === "NONE") return null;

    const groups: { [key: string]: PhotoEntity[] } = {};

    sortedPhotos.forEach((photo) => {
      let key = "Uncategorized";
      if (groupingMode === "SCENES") {
        key = photo.environment || "Scenic & Landscapes";
      } else if (groupingMode === "ATTIRE") {
        key = photo.attire || "No Person / Casual";
      } else if (groupingMode === "ENVIRONMENT") {
        key = photo.environment || "Outdoor Landscapes";
      } else if (groupingMode === "LIGHTING") {
        key = photo.lighting || "Standard Ambient Light";
      }

      if (!groups[key]) {
        groups[key] = [];
      }
      groups[key].push(photo);
    });

    return Object.entries(groups).sort((a, b) => a[0].localeCompare(b[0]));
  }, [sortedPhotos, groupingMode]);

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

  const renderPhotoItem = (photo: PhotoEntity) => {
    const isVid = photo.durationSeconds > 0;
    
    // Apply temporary/active CSS filter effect on thumbnail based on currentFilter
    const getFilterCSS = (filterName: string | null) => {
      switch (filterName) {
        case "Vintage":
          return "sepia brightness-90 contrast-110 saturate-120";
        case "Mono":
          return "grayscale contrast-125";
        case "Warm Glow":
          return "sepia-30 brightness-110 saturate-130";
        case "Cool Mist":
          return "contrast-95 saturate-80 brightness-105 hue-rotate-15";
        case "Cyberpunk":
          return "hue-rotate-60 saturate-200";
        default:
          return "";
      }
    };

    if (viewLayout === "list") {
      return (
        <div
          key={photo.id}
          onClick={() => onPhotoSelect(photo)}
          className="flex items-center gap-4 bg-white border border-gray-150 rounded-xl p-3 cursor-pointer hover:bg-gray-50/80 hover:shadow-sm transition"
        >
          <div className="relative w-16 h-16 rounded-lg overflow-hidden border border-gray-100 shrink-0">
            <img
              src={`/images/${photo.resourceName}.jpg`}
              alt={photo.title}
              referrerPolicy="no-referrer"
              className={`w-full h-full object-cover ${getFilterCSS(photo.currentFilter)}`}
            />
            {isVid && (
              <div className="absolute inset-0 bg-black/30 flex items-center justify-center">
                <Play size={16} fill="white" className="text-white" />
              </div>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <h4 className="text-sm font-semibold text-gray-900 truncate">{photo.title}</h4>
            <p className="text-xs text-gray-500 truncate mt-0.5 font-mono">
              {new Date(photo.dateTaken).toLocaleDateString()} · {formatBytes(photo.sizeBytes)}
              {isVid && ` · video (${formatDuration(photo.durationSeconds)})`}
            </p>
            {photo.faceTag && (
              <span className="inline-block mt-1 text-[10px] font-bold bg-brand-purple/10 text-brand-purple px-1.5 py-0.5 rounded">
                Category: {photo.faceTag}
              </span>
            )}
          </div>
        </div>
      );
    }

    // Grid Layout
    return (
      <div
        key={photo.id}
        onClick={() => onPhotoSelect(photo)}
        className="group relative bg-white border border-gray-150 rounded-2xl overflow-hidden cursor-pointer hover:shadow-md transition duration-200"
      >
        <div className="relative aspect-square w-full bg-gray-100 overflow-hidden">
          <img
            src={`/images/${photo.resourceName}.jpg`}
            alt={photo.title}
            referrerPolicy="no-referrer"
            className={`w-full h-full object-cover group-hover:scale-105 transition-transform duration-300 ${getFilterCSS(
              photo.currentFilter
            )}`}
          />

          {/* Overlays */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col justify-end p-3 text-white">
            <span className="text-[10px] font-semibold tracking-wide text-brand-purple-container bg-brand-purple/90 self-start px-2 py-0.5 rounded-full mb-1">
              {photo.faceTag || "No Face"}
            </span>
            <p className="text-xs font-medium truncate">{photo.title}</p>
          </div>

          {/* Media Indicators */}
          <div className="absolute top-2.5 left-2.5 flex items-center justify-center w-6 h-6 rounded-lg bg-black/40 backdrop-blur-md text-white border border-white/10">
            {isVid ? <Play size={11} fill="white" /> : <ImageIcon size={11} />}
          </div>

          {isVid && (
            <span className="absolute bottom-2.5 right-2.5 text-[9px] font-mono font-bold text-white bg-black/60 px-1.5 py-0.5 rounded border border-white/10">
              {formatDuration(photo.durationSeconds)}
            </span>
          )}
        </div>
        <div className="p-3">
          <h4 className="text-xs font-bold text-gray-900 truncate group-hover:text-brand-purple transition">
            {photo.title}
          </h4>
          <div className="flex items-center justify-between mt-1 text-[9px] text-gray-500 font-mono">
            <span>{new Date(photo.dateTaken).toLocaleDateString()}</span>
            <span>{formatBytes(photo.sizeBytes)}</span>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Sub-Album Header */}
      {selectedFaceAlbum && (
        <div className="bg-gradient-to-r from-brand-purple/10 to-transparent p-4 rounded-2xl border border-brand-purple/20 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={onExitFaceAlbum}
              className="p-2 text-gray-700 bg-white rounded-xl shadow-sm border border-gray-200 hover:bg-gray-50 transition"
            >
              <ChevronLeft size={16} />
            </button>
            <div>
              <span className="text-[10px] uppercase font-bold tracking-wider text-brand-purple">
                Aura Smart Album
              </span>
              <h2 className="text-xl font-bold text-gray-900">
                Category: {selectedFaceAlbum}
              </h2>
            </div>
          </div>
          <div className="text-right shrink-0">
            <span className="text-xs font-bold font-mono text-gray-500 block">
              {albumPhotos.length} {albumPhotos.length === 1 ? "item" : "items"}
            </span>
            <button
              onClick={onExitFaceAlbum}
              className="text-[10px] font-bold text-brand-purple hover:underline"
            >
              Back to Main Gallery
            </button>
          </div>
        </div>
      )}

      {/* Control Panel: Search & Filters */}
      <div className="bg-white rounded-2xl border border-gray-150 p-4 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Search Input */}
        <div className="relative flex-1">
          <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
            <Search size={16} />
          </span>
          <input
            type="text"
            placeholder="Search by title, attire, environment, lighting, category..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-brand-purple focus:border-brand-purple transition bg-gray-50/50"
          />
        </div>

        {/* Filters/Sorts Row */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Sorting Dropdown */}
          <div className="flex items-center gap-1.5 border border-gray-200 px-3 py-1.5 rounded-xl text-xs bg-white">
            <ArrowUpDown size={13} className="text-gray-500" />
            <select
              value={sortOption}
              onChange={(e) => setSortOption(e.target.value as SortOption)}
              className="bg-transparent font-medium text-gray-700 focus:outline-none cursor-pointer"
            >
              <option value="date_desc">Newest First</option>
              <option value="date_asc">Oldest First</option>
              <option value="size_desc">Largest Size</option>
              <option value="size_asc">Smallest Size</option>
            </select>
          </div>

          {/* Layout Toggle */}
          <div className="flex items-center bg-gray-100 p-1 rounded-xl border border-gray-200 shrink-0">
            <button
              onClick={() => setViewLayout("grid")}
              className={`p-1.5 rounded-lg transition ${
                viewLayout === "grid" ? "bg-white text-brand-purple shadow-sm" : "text-gray-500 hover:text-gray-900"
              }`}
              title="Grid Layout"
            >
              <Grid size={15} />
            </button>
            <button
              onClick={() => setViewLayout("list")}
              className={`p-1.5 rounded-lg transition ${
                viewLayout === "list" ? "bg-white text-brand-purple shadow-sm" : "text-gray-500 hover:text-gray-900"
              }`}
              title="List Layout"
            >
              <List size={15} />
            </button>
          </div>
        </div>
      </div>

      {/* Nested Multi-Grouping Navigation Rails */}
      <div className="flex flex-col gap-2">
        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider pl-1">
          Folder Grouping Mode
        </span>
        <div className="flex flex-wrap gap-2">
          {[
            { id: "NONE", label: "Flat Feed" },
            { id: "SCENES", label: "Smart Scenes" },
            { id: "ATTIRE", label: "By Attire" },
            { id: "ENVIRONMENT", label: "By Environment" },
            { id: "LIGHTING", label: "By Lighting" }
          ].map((mode) => (
            <button
              key={mode.id}
              onClick={() => setGroupingMode(mode.id as GroupingMode)}
              className={`text-xs font-semibold px-4 py-1.5 rounded-full transition border ${
                groupingMode === mode.id
                  ? "bg-brand-purple border-brand-purple text-white shadow-sm"
                  : "bg-white border-gray-200 text-gray-600 hover:bg-gray-50"
              }`}
            >
              {mode.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Stream Section */}
      {sortedPhotos.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-16 bg-white rounded-2xl border border-gray-150 text-center shadow-sm">
          <FolderOpen size={40} className="text-gray-300 mb-3" />
          <h3 className="text-base font-bold text-gray-700">No media found</h3>
          <p className="text-xs text-gray-500 mt-1 max-w-xs leading-relaxed">
            No items matched the filters. Simulating imports or resetting scanning will repopulate active galleries.
          </p>
        </div>
      ) : groupingMode === "NONE" ? (
        // Flat list rendering
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
        // Grouped nested folders
        <div className="flex flex-col gap-8">
          {groupedSections?.map(([sectionTitle, sectionPhotos]) => (
            <div key={sectionTitle} className="flex flex-col gap-4">
              {/* Group Header */}
              <div className="flex items-center gap-2 border-b border-gray-200 pb-2">
                <FolderOpen size={16} className="text-brand-purple" />
                <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wide">
                  {sectionTitle}
                </h3>
                <span className="text-[10px] font-mono font-semibold bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
                  {sectionPhotos.length} {sectionPhotos.length === 1 ? "item" : "items"}
                </span>
              </div>

              {/* Nested grid inside group */}
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
    </div>
  );
}
