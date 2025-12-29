import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/audiobook.dart';
import '../../models/collection.dart';
import '../../providers/auth_provider.dart';
import '../../providers/detail_provider.dart';
import '../../providers/download_provider.dart';
import '../../providers/player_provider.dart';
import '../../services/api_service.dart'
    show ApiService, AudiobookUpdateRequest, MetadataSearchResult;
import '../../services/download_service.dart';
import '../../theme/app_theme.dart';

/// Audiobook detail screen matching Android AudiobookDetailScreen.kt
class AudiobookDetailScreen extends StatefulWidget {
  final int audiobookId;
  final VoidCallback? onBackClick;
  final Function(int, int?)? onPlayClick;
  final Function(String)? onAuthorClick;
  final Function(String)? onSeriesClick;
  final Function(String)? onGenreClick;

  const AudiobookDetailScreen({
    super.key,
    required this.audiobookId,
    this.onBackClick,
    this.onPlayClick,
    this.onAuthorClick,
    this.onSeriesClick,
    this.onGenreClick,
  });

  @override
  State<AudiobookDetailScreen> createState() => _AudiobookDetailScreenState();
}

class _AudiobookDetailScreenState extends State<AudiobookDetailScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DetailProvider>().loadAudiobook(widget.audiobookId);
    });
  }

  void _showRecapDialog(BuildContext context, DetailProvider detail) {
    detail.loadRecap();
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => _RecapDialog(detail: detail),
    );
  }

  void _showEditDialog(
    BuildContext context,
    Audiobook book,
    DetailProvider detail,
  ) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _EditMetadataSheet(audiobook: book, detail: detail),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;
    final auth = context.watch<AuthProvider>();
    final player = context.watch<PlayerProvider>();
    final isAdmin = auth.user?.isAdminUser == true;

    return Container(
      color: sapphoBackground,
      child: Consumer<DetailProvider>(
        builder: (context, detail, child) {
          if (detail.isLoading) {
            return const Center(
              child: CircularProgressIndicator(color: sapphoInfo),
            );
          }

          final book = detail.audiobook;
          if (book == null) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    'Failed to load audiobook',
                    style: TextStyle(color: sapphoTextMuted),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: widget.onBackClick,
                    child: const Text('Go Back'),
                  ),
                ],
              ),
            );
          }

          return SingleChildScrollView(
            padding: EdgeInsets.only(bottom: bottomPadding + 80),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Back and Edit buttons
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      OutlinedButton.icon(
                        onPressed: widget.onBackClick,
                        icon: const Icon(Icons.arrow_back, size: 18),
                        label: const Text('Back'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: sapphoIconDefault,
                          side: const BorderSide(color: sapphoProgressTrack),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                      ),
                      // Edit button (admin only)
                      if (isAdmin)
                        OutlinedButton.icon(
                          onPressed: () =>
                              _showEditDialog(context, book, detail),
                          icon: const Icon(Icons.edit, size: 18),
                          label: const Text('Edit'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: sapphoInfo,
                            side: BorderSide(
                              color: sapphoInfo.withValues(alpha: 0.3),
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),

                // Cover image with progress and favorite button
                _CoverSection(
                  book: book,
                  progress: detail.progress,
                  serverUrl: detail.serverUrl,
                  authToken: detail.authToken,
                  isFavorite: detail.isFavorite,
                  isTogglingFavorite: detail.isTogglingFavorite,
                  onToggleFavorite: () => detail.toggleFavorite(),
                ),

                // Rating section
                const SizedBox(height: 16),
                _RatingSection(
                  userRating: detail.userRating,
                  averageRating: detail.averageRatingValue,
                  ratingCount: detail.ratingCount,
                  isUpdating: detail.isUpdatingRating,
                  onRate: (rating) => detail.setRating(rating),
                  onClear: () => detail.clearRating(),
                ),

                const SizedBox(height: 24),

                // Play button
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: _PlayButton(
                    book: book,
                    progress: detail.progress,
                    isThisBookLoaded: player.currentAudiobook?.id == book.id,
                    isPlaying: player.isPlaying,
                    onPlay: () => widget.onPlayClick?.call(
                      book.id,
                      detail.progress?.position,
                    ),
                    onTogglePlayPause: () => player.togglePlayPause(),
                  ),
                ),

                const SizedBox(height: 16),

                // Chapters and Download row
                if (detail.chapters.isNotEmpty || true) // Always show download
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: Row(
                      children: [
                        if (detail.chapters.isNotEmpty)
                          Expanded(
                            child: _ChaptersButton(
                              chapters: detail.chapters,
                              book: book,
                              progress: detail.progress,
                            ),
                          ),
                        if (detail.chapters.isNotEmpty)
                          const SizedBox(width: 12),
                        Expanded(child: _DownloadButton(audiobook: book)),
                      ],
                    ),
                  ),

                const SizedBox(height: 16),

                // Action buttons
                _ActionButtons(
                  audiobookId: book.id,
                  hasProgress:
                      (detail.progress?.position ?? 0) > 0 ||
                      detail.progress?.completed == 1,
                  isRefreshingMetadata: detail.isRefreshingMetadata,
                  isAdmin: isAdmin,
                  onMarkFinished: () => detail.markFinished(),
                  onClearProgress: () => detail.clearProgress(),
                  onRefresh: () => detail.refreshMetadata(),
                  onDelete: () {
                    // TODO: Implement delete audiobook
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Delete feature coming soon'),
                      ),
                    );
                  },
                ),

                // Progress section
                if (detail.progress != null &&
                    (detail.progress!.position > 0 ||
                        detail.progress!.completed == 1))
                  _ProgressSection(
                    progress: detail.progress!,
                    duration: book.duration,
                    chapters: detail.chapters,
                  ),

                // Description (About section)
                if (book.description != null &&
                    book.description!.isNotEmpty) ...[
                  const SizedBox(height: 32),
                  _AboutSection(
                    description: book.description!,
                    showCatchMeUp: detail.showCatchMeUp,
                    onCatchMeUp: () => _showRecapDialog(context, detail),
                  ),
                ],

                // Metadata section
                const SizedBox(height: 32),
                _MetadataSection(
                  book: book,
                  onAuthorClick: widget.onAuthorClick,
                  onSeriesClick: widget.onSeriesClick,
                  onGenreClick: widget.onGenreClick,
                ),

                // Files dropdown
                if (detail.files.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  _FilesDropdown(files: detail.files),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

/// Cover section with image, favorite button, and progress bar
class _CoverSection extends StatelessWidget {
  final Audiobook book;
  final Progress? progress;
  final String? serverUrl;
  final String? authToken;
  final bool isFavorite;
  final bool isTogglingFavorite;
  final VoidCallback onToggleFavorite;

  const _CoverSection({
    required this.book,
    this.progress,
    required this.serverUrl,
    required this.authToken,
    required this.isFavorite,
    required this.isTogglingFavorite,
    required this.onToggleFavorite,
  });

  @override
  Widget build(BuildContext context) {
    final hasProgress =
        progress != null &&
        book.duration != null &&
        book.duration! > 0 &&
        (progress!.position > 0 || progress!.completed == 1);

    final progressPercent = hasProgress
        ? (progress!.completed == 1
              ? 1.0
              : (progress!.position / book.duration!).clamp(0.01, 1.0))
        : 0.0;

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: SizedBox(
          width: 320,
          height: 320,
          child: Stack(
            children: [
              // Cover image with shadow
              Container(
                width: 320,
                height: 320,
                decoration: BoxDecoration(
                  color: sapphoProgressTrack,
                  borderRadius: BorderRadius.circular(20),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.6),
                      blurRadius: 60,
                      offset: const Offset(0, 20),
                    ),
                  ],
                ),
                clipBehavior: Clip.antiAlias,
                child: book.coverImage != null && serverUrl != null
                    ? CachedNetworkImage(
                        imageUrl: '$serverUrl/api/audiobooks/${book.id}/cover',
                        fit: BoxFit.cover,
                        memCacheWidth: 640,
                        memCacheHeight: 640,
                        fadeInDuration: Duration.zero,
                        fadeOutDuration: Duration.zero,
                        httpHeaders: authToken != null
                            ? {'Authorization': 'Bearer $authToken'}
                            : null,
                        placeholder: (_, __) => _buildPlaceholder(),
                        errorWidget: (_, __, ___) => _buildPlaceholder(),
                      )
                    : _buildPlaceholder(),
              ),

              // Favorite button (top-right)
              Positioned(
                top: 12,
                right: 12,
                child: GestureDetector(
                  onTap: isTogglingFavorite ? null : onToggleFavorite,
                  child: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.5),
                      shape: BoxShape.circle,
                    ),
                    child: isTogglingFavorite
                        ? const Padding(
                            padding: EdgeInsets.all(10),
                            child: CircularProgressIndicator(
                              color: sapphoInfo,
                              strokeWidth: 2,
                            ),
                          )
                        : Icon(
                            isFavorite
                                ? Icons.bookmark_added
                                : Icons.bookmark_border,
                            color: isFavorite ? sapphoInfo : Colors.white,
                            size: 22,
                          ),
                  ),
                ),
              ),

              // Progress bar at bottom
              if (hasProgress)
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    height: 6,
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.5),
                      borderRadius: const BorderRadius.only(
                        bottomLeft: Radius.circular(20),
                        bottomRight: Radius.circular(20),
                      ),
                    ),
                    child: FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: progressPercent,
                      child: Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: progress!.completed == 1
                                ? [sapphoSuccess, legacyGreenLight]
                                : [sapphoInfo, legacyBlueLight],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Center(
      child: Text(
        book.title.length >= 2
            ? book.title.substring(0, 2).toUpperCase()
            : book.title.toUpperCase(),
        style: const TextStyle(
          color: sapphoInfo,
          fontSize: 72,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

/// Rating section with 5 stars
class _RatingSection extends StatelessWidget {
  final int? userRating;
  final double? averageRating;
  final int? ratingCount;
  final bool isUpdating;
  final Function(int) onRate;
  final VoidCallback onClear;

  const _RatingSection({
    required this.userRating,
    this.averageRating,
    this.ratingCount,
    required this.isUpdating,
    required this.onRate,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Stars row
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (int i = 1; i <= 5; i++)
              GestureDetector(
                onTap: isUpdating
                    ? null
                    : () {
                        if (userRating == i) {
                          onClear();
                        } else {
                          onRate(i);
                        }
                      },
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: Icon(
                    userRating != null && i <= userRating!
                        ? Icons.star
                        : Icons.star_border,
                    color: userRating != null && i <= userRating!
                        ? sapphoStarFilled
                        : sapphoTextMuted,
                    size: 32,
                  ),
                ),
              ),
            if (isUpdating) ...[
              const SizedBox(width: 8),
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(
                  color: sapphoStarFilled,
                  strokeWidth: 2,
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 4),
        // Rating info text
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              userRating != null ? 'Your rating' : 'Tap to rate',
              style: TextStyle(
                fontSize: 12,
                color: userRating != null ? sapphoIconDefault : sapphoTextMuted,
              ),
            ),
            if (averageRating != null && (ratingCount ?? 0) > 0) ...[
              const Text(
                '  •  ',
                style: TextStyle(fontSize: 12, color: sapphoTextMuted),
              ),
              const Icon(Icons.star, color: sapphoStarFilled, size: 14),
              const SizedBox(width: 2),
              Text(
                averageRating!.toStringAsFixed(1),
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: Colors.white,
                ),
              ),
              Text(
                ' ($ratingCount)',
                style: const TextStyle(fontSize: 12, color: sapphoIconDefault),
              ),
            ],
          ],
        ),
      ],
    );
  }
}

/// Play/Pause button
class _PlayButton extends StatelessWidget {
  final Audiobook book;
  final Progress? progress;
  final bool isThisBookLoaded;
  final bool isPlaying;
  final VoidCallback? onPlay;
  final VoidCallback? onTogglePlayPause;

  const _PlayButton({
    required this.book,
    this.progress,
    this.isThisBookLoaded = false,
    this.isPlaying = false,
    this.onPlay,
    this.onTogglePlayPause,
  });

  @override
  Widget build(BuildContext context) {
    final hasProgress = (progress?.position ?? 0) > 0;
    final isCompleted = progress?.completed == 1;
    final isThisBookPlaying = isThisBookLoaded && isPlaying;

    // Determine button text
    String buttonText;
    if (isThisBookPlaying) {
      buttonText = 'Pause';
    } else if (isCompleted) {
      buttonText = 'Listen Again';
    } else if (hasProgress) {
      buttonText = 'Continue';
    } else {
      buttonText = 'Play';
    }

    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: () {
          if (isThisBookLoaded) {
            // Book is already loaded, just toggle play/pause
            onTogglePlayPause?.call();
          } else {
            // Start playing a new book
            onPlay?.call();
          }
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: isThisBookPlaying
              ? sapphoInfo.withValues(alpha: 0.15)
              : sapphoSuccess.withValues(alpha: 0.15),
          foregroundColor: isThisBookPlaying ? legacyBluePale : legacyGreenPale,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Colors.white.withValues(alpha: 0.1)),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(isThisBookPlaying ? Icons.pause : Icons.play_arrow, size: 24),
            const SizedBox(width: 8),
            Text(
              buttonText,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

/// Chapters button
class _ChaptersButton extends StatelessWidget {
  final List<Chapter> chapters;
  final Audiobook book;
  final Progress? progress;

  const _ChaptersButton({
    required this.chapters,
    required this.book,
    this.progress,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 48,
      child: ElevatedButton(
        onPressed: () => _showChaptersDialog(context),
        style: ElevatedButton.styleFrom(
          backgroundColor: sapphoInfo.withValues(alpha: 0.15),
          foregroundColor: legacyBluePale,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Colors.white.withValues(alpha: 0.1)),
          ),
        ),
        child: Text(
          '${chapters.length} Chapters',
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }

  void _showChaptersDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: sapphoSurface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) => _ChaptersBottomSheet(
        chapters: chapters,
        currentPosition: progress?.position,
      ),
    );
  }
}

/// Download button
class _DownloadButton extends StatelessWidget {
  final Audiobook audiobook;

  const _DownloadButton({required this.audiobook});

  @override
  Widget build(BuildContext context) {
    return Consumer<DownloadProvider>(
      builder: (context, downloadProvider, _) {
        final status = downloadProvider.getDownloadStatus(audiobook.id);
        final progress = downloadProvider.getProgress(audiobook.id);

        // Determine button state
        final isDownloaded = status == DownloadStatus.completed;
        final isDownloading =
            status == DownloadStatus.downloading ||
            status == DownloadStatus.pending;
        final isFailed = status == DownloadStatus.failed;

        // Button colors based on state
        Color bgColor;
        Color fgColor;
        if (isDownloaded) {
          bgColor = sapphoSuccess.withValues(alpha: 0.15);
          fgColor = legacyGreenPale;
        } else if (isDownloading) {
          bgColor = sapphoInfo.withValues(alpha: 0.15);
          fgColor = legacyBluePale;
        } else if (isFailed) {
          bgColor = sapphoError.withValues(alpha: 0.15);
          fgColor = sapphoError;
        } else {
          bgColor = sapphoInfo.withValues(alpha: 0.15);
          fgColor = legacyBluePale;
        }

        return SizedBox(
          height: 48,
          child: Stack(
            children: [
              // Progress indicator (behind button)
              if (isDownloading && progress > 0)
                Positioned.fill(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: LinearProgressIndicator(
                      value: progress,
                      backgroundColor: Colors.transparent,
                      valueColor: AlwaysStoppedAnimation(
                        sapphoInfo.withValues(alpha: 0.3),
                      ),
                    ),
                  ),
                ),
              // Button
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: () =>
                      _handleTap(context, downloadProvider, status),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: bgColor,
                    foregroundColor: fgColor,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(
                        color: Colors.white.withValues(alpha: 0.1),
                      ),
                    ),
                  ),
                  child: _buildButtonContent(status, progress),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildButtonContent(DownloadStatus? status, double progress) {
    if (status == DownloadStatus.completed) {
      return const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.check_circle, size: 18),
          SizedBox(width: 6),
          Text(
            'Downloaded',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
        ],
      );
    }

    if (status == DownloadStatus.downloading) {
      final percent = (progress * 100).toInt();
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const SizedBox(
            width: 16,
            height: 16,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation(legacyBluePale),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            '$percent%',
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
        ],
      );
    }

    if (status == DownloadStatus.pending) {
      return const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 16,
            height: 16,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation(legacyBluePale),
            ),
          ),
          SizedBox(width: 8),
          Text(
            'Starting...',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
        ],
      );
    }

    if (status == DownloadStatus.failed) {
      return const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 18),
          SizedBox(width: 6),
          Text(
            'Retry',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
        ],
      );
    }

    // Default: not downloaded
    return const Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.download, size: 18),
        SizedBox(width: 6),
        Text(
          'Download',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  void _handleTap(
    BuildContext context,
    DownloadProvider provider,
    DownloadStatus? status,
  ) async {
    if (status == DownloadStatus.completed) {
      // Show options to delete
      _showDownloadedOptions(context, provider);
    } else if (status == DownloadStatus.downloading ||
        status == DownloadStatus.pending) {
      // Cancel download
      await provider.cancelDownload(audiobook.id);
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Download cancelled')));
      }
    } else if (status == DownloadStatus.failed) {
      // Retry download
      try {
        await provider.retryDownload(audiobook);
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to start download: $e')),
          );
        }
      }
    } else {
      // Start download
      try {
        // Show "started" message immediately
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('Download started')));
        }
        await provider.startDownload(audiobook);
        // Show "complete" message when done
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('Download complete')));
        }
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Download failed: $e')));
        }
      }
    }
  }

  void _showDownloadedOptions(BuildContext context, DownloadProvider provider) {
    showModalBottomSheet(
      context: context,
      backgroundColor: sapphoSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.symmetric(vertical: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.check_circle, color: sapphoSuccess),
              title: const Text(
                'Downloaded',
                style: TextStyle(color: sapphoText),
              ),
              subtitle: const Text(
                'This audiobook is available offline',
                style: TextStyle(color: sapphoTextMuted, fontSize: 12),
              ),
            ),
            const Divider(color: sapphoSurfaceBorder),
            ListTile(
              leading: const Icon(Icons.delete_outline, color: sapphoError),
              title: const Text(
                'Remove Download',
                style: TextStyle(color: sapphoError),
              ),
              onTap: () async {
                Navigator.pop(context);
                await provider.deleteDownload(audiobook.id);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Download removed')),
                  );
                }
              },
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}

/// Action buttons (Collection, Mark Finished, Clear Progress, Refresh, Delete)
class _ActionButtons extends StatelessWidget {
  final int audiobookId;
  final bool hasProgress;
  final bool isRefreshingMetadata;
  final bool isAdmin;
  final VoidCallback onMarkFinished;
  final VoidCallback onClearProgress;
  final VoidCallback onRefresh;
  final VoidCallback onDelete;

  const _ActionButtons({
    required this.audiobookId,
    required this.hasProgress,
    required this.isRefreshingMetadata,
    required this.isAdmin,
    required this.onMarkFinished,
    required this.onClearProgress,
    required this.onRefresh,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          // Collection button
          OutlinedButton.icon(
            onPressed: () => _showCollectionsDialog(context),
            icon: const Icon(Icons.folder_outlined, size: 16),
            label: const Text(
              'Collections',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
            style: OutlinedButton.styleFrom(
              foregroundColor: sapphoInfo,
              side: BorderSide(color: sapphoInfo.withValues(alpha: 0.3)),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            ),
          ),
          // Mark Finished
          OutlinedButton(
            onPressed: onMarkFinished,
            style: OutlinedButton.styleFrom(
              foregroundColor: sapphoSuccess,
              side: BorderSide(color: sapphoSuccess.withValues(alpha: 0.3)),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            ),
            child: const Text(
              'Mark Finished',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ),
          // Clear Progress (only if has progress)
          if (hasProgress)
            OutlinedButton(
              onPressed: onClearProgress,
              style: OutlinedButton.styleFrom(
                foregroundColor: sapphoWarning,
                side: BorderSide(color: sapphoWarning.withValues(alpha: 0.3)),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
              ),
              child: const Text(
                'Clear Progress',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
              ),
            ),
          // Refresh metadata button
          OutlinedButton.icon(
            onPressed: isRefreshingMetadata ? null : onRefresh,
            icon: isRefreshingMetadata
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      color: sapphoIconDefault,
                      strokeWidth: 2,
                    ),
                  )
                : const Icon(Icons.refresh, size: 16),
            label: Text(
              isRefreshingMetadata ? 'Refreshing...' : 'Refresh',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
            style: OutlinedButton.styleFrom(
              foregroundColor: sapphoIconDefault,
              side: const BorderSide(color: sapphoProgressTrack),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            ),
          ),
          // Delete button (admin only)
          if (isAdmin)
            OutlinedButton.icon(
              onPressed: onDelete,
              icon: const Icon(Icons.delete, size: 16),
              label: const Text(
                'Delete',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
              ),
              style: OutlinedButton.styleFrom(
                foregroundColor: sapphoError,
                side: BorderSide(color: sapphoError.withValues(alpha: 0.3)),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
              ),
            ),
        ],
      ),
    );
  }

  void _showCollectionsDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: sapphoSurface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) => _CollectionsBottomSheet(audiobookId: audiobookId),
    );
  }
}

/// Bottom sheet for managing book collections
class _CollectionsBottomSheet extends StatefulWidget {
  final int audiobookId;

  const _CollectionsBottomSheet({required this.audiobookId});

  @override
  State<_CollectionsBottomSheet> createState() =>
      _CollectionsBottomSheetState();
}

class _CollectionsBottomSheetState extends State<_CollectionsBottomSheet> {
  List<Collection> _collections = [];
  Set<int> _memberCollections = {};
  bool _isLoading = true;
  bool _isUpdating = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadCollections();
  }

  Future<void> _loadCollections() async {
    try {
      final api = context.read<ApiService>();
      final collections = await api.getCollections();

      // Check which collections contain this book
      final memberIds = <int>{};
      for (final collection in collections) {
        try {
          final detail = await api.getCollection(collection.id);
          if (detail.books.any((b) => b.id == widget.audiobookId)) {
            memberIds.add(collection.id);
          }
        } catch (e) {
          // Skip if can't load collection detail
        }
      }

      if (mounted) {
        setState(() {
          _collections = collections;
          _memberCollections = memberIds;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _toggleCollection(int collectionId) async {
    setState(() => _isUpdating = true);

    try {
      final api = context.read<ApiService>();
      final isMember = _memberCollections.contains(collectionId);

      if (isMember) {
        await api.removeFromCollection(collectionId, widget.audiobookId);
        setState(() => _memberCollections.remove(collectionId));
      } else {
        await api.addToCollection(collectionId, widget.audiobookId);
        setState(() => _memberCollections.add(collectionId));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Error: ${e.toString()}')));
      }
    } finally {
      if (mounted) {
        setState(() => _isUpdating = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return DraggableScrollableSheet(
      initialChildSize: 0.5,
      minChildSize: 0.3,
      maxChildSize: 0.8,
      expand: false,
      builder: (context, scrollController) {
        return Column(
          children: [
            // Handle
            Container(
              margin: const EdgeInsets.symmetric(vertical: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            // Title
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Add to Collection',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                      color: sapphoText,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: sapphoIconDefault),
                  ),
                ],
              ),
            ),
            const Divider(color: sapphoSurfaceBorder),
            // Content
            Expanded(
              child: _isLoading
                  ? const Center(
                      child: CircularProgressIndicator(color: sapphoInfo),
                    )
                  : _error != null
                  ? Center(
                      child: Text(
                        _error!,
                        style: const TextStyle(color: sapphoError),
                      ),
                    )
                  : _collections.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(
                            Icons.folder_outlined,
                            size: 48,
                            color: sapphoIconDefault,
                          ),
                          const SizedBox(height: 16),
                          const Text(
                            'No collections yet',
                            style: TextStyle(color: sapphoText, fontSize: 16),
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'Create a collection from the menu',
                            style: TextStyle(
                              color: sapphoIconDefault,
                              fontSize: 14,
                            ),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      controller: scrollController,
                      padding: EdgeInsets.fromLTRB(
                        16,
                        8,
                        16,
                        bottomPadding + 16,
                      ),
                      itemCount: _collections.length,
                      itemBuilder: (context, index) {
                        final collection = _collections[index];
                        final isMember = _memberCollections.contains(
                          collection.id,
                        );
                        return _CollectionItem(
                          collection: collection,
                          isMember: isMember,
                          isUpdating: _isUpdating,
                          onToggle: () => _toggleCollection(collection.id),
                        );
                      },
                    ),
            ),
          ],
        );
      },
    );
  }
}

class _CollectionItem extends StatelessWidget {
  final Collection collection;
  final bool isMember;
  final bool isUpdating;
  final VoidCallback onToggle;

  const _CollectionItem({
    required this.collection,
    required this.isMember,
    required this.isUpdating,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: isMember
            ? sapphoInfo.withValues(alpha: 0.1)
            : sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          onTap: isUpdating ? null : onToggle,
          borderRadius: BorderRadius.circular(8),
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              border: isMember
                  ? Border.all(color: sapphoInfo.withValues(alpha: 0.3))
                  : null,
            ),
            child: Row(
              children: [
                Icon(
                  isMember ? Icons.folder : Icons.folder_outlined,
                  color: isMember ? sapphoInfo : sapphoIconDefault,
                  size: 24,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        collection.name,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color: isMember ? sapphoInfo : sapphoText,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        '${collection.bookCount} books',
                        style: const TextStyle(
                          fontSize: 12,
                          color: sapphoIconDefault,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 24,
                  height: 24,
                  decoration: BoxDecoration(
                    color: isMember ? sapphoInfo : Colors.transparent,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isMember ? sapphoInfo : sapphoIconDefault,
                      width: 2,
                    ),
                  ),
                  child: isMember
                      ? const Icon(Icons.check, color: Colors.white, size: 16)
                      : null,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Progress section showing detailed progress info
class _ProgressSection extends StatelessWidget {
  final Progress progress;
  final int? duration;
  final List<Chapter> chapters;

  const _ProgressSection({
    required this.progress,
    this.duration,
    required this.chapters,
  });

  @override
  Widget build(BuildContext context) {
    final progressHours = progress.position ~/ 3600;
    final progressMinutes = (progress.position % 3600) ~/ 60;
    final totalHours = (duration ?? 0) ~/ 3600;
    final totalMinutes = ((duration ?? 0) % 3600) ~/ 60;
    final percentage = duration != null && duration! > 0
        ? (progress.position / duration! * 100).toInt()
        : 0;

    // Find current chapter
    Chapter? currentChapter;
    int currentChapterIndex = 0;
    if (chapters.isNotEmpty && progress.completed != 1) {
      for (int i = chapters.length - 1; i >= 0; i--) {
        if (progress.position >= chapters[i].startTime) {
          currentChapter = chapters[i];
          currentChapterIndex = i;
          break;
        }
      }
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 24, 24, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Progress',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: sapphoSurfaceLight.withValues(alpha: 0.5),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Time progress
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          progress.completed == 1
                              ? 'Completed'
                              : '${progressHours}h ${progressMinutes}m listened',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w500,
                            color: progress.completed == 1
                                ? sapphoSuccess
                                : Colors.white,
                          ),
                        ),
                        if (progress.completed != 1)
                          Text(
                            'of ${totalHours}h ${totalMinutes}m total',
                            style: const TextStyle(
                              fontSize: 14,
                              color: sapphoIconDefault,
                            ),
                          ),
                      ],
                    ),
                    if (progress.completed != 1)
                      Text(
                        '$percentage%',
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                          color: sapphoInfo,
                        ),
                      ),
                  ],
                ),

                // Progress bar
                if (progress.completed != 1 &&
                    duration != null &&
                    duration! > 0) ...[
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(3),
                    child: Container(
                      height: 6,
                      width: double.infinity,
                      color: sapphoProgressTrack,
                      child: FractionallySizedBox(
                        alignment: Alignment.centerLeft,
                        widthFactor: (percentage / 100).clamp(0.0, 1.0),
                        heightFactor: 1.0,
                        child: Container(
                          decoration: const BoxDecoration(
                            gradient: LinearGradient(
                              colors: [sapphoInfo, legacyBlueLight],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],

                // Current chapter
                if (currentChapter != null && progress.completed != 1) ...[
                  const SizedBox(height: 12),
                  const Divider(color: Colors.white10, height: 1),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Current Chapter',
                              style: TextStyle(
                                fontSize: 12,
                                color: sapphoIconDefault,
                              ),
                            ),
                            Text(
                              currentChapter.title ??
                                  'Chapter ${currentChapterIndex + 1}',
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                                color: Colors.white,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      Text(
                        '${currentChapterIndex + 1} of ${chapters.length}',
                        style: const TextStyle(
                          fontSize: 14,
                          color: sapphoIconDefault,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// About section (description) with optional Catch Me Up button
class _AboutSection extends StatefulWidget {
  final String description;
  final bool showCatchMeUp;
  final VoidCallback? onCatchMeUp;

  const _AboutSection({
    required this.description,
    this.showCatchMeUp = false,
    this.onCatchMeUp,
  });

  @override
  State<_AboutSection> createState() => _AboutSectionState();
}

class _AboutSectionState extends State<_AboutSection> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header row with About title and Catch Up button
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Text(
                'About',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
              if (widget.showCatchMeUp)
                TextButton.icon(
                  onPressed: widget.onCatchMeUp,
                  icon: const Icon(Icons.auto_awesome, size: 16),
                  label: const Text(
                    'Catch Up',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                  ),
                  style: TextButton.styleFrom(
                    foregroundColor: sapphoAccentLight,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            widget.description,
            style: const TextStyle(
              fontSize: 16,
              color: sapphoTextLight,
              height: 1.6,
            ),
            maxLines: _expanded ? null : 4,
            overflow: _expanded ? null : TextOverflow.ellipsis,
          ),
          if (widget.description.length > 200)
            TextButton(
              onPressed: () => setState(() => _expanded = !_expanded),
              style: TextButton.styleFrom(padding: EdgeInsets.zero),
              child: Text(
                _expanded ? 'Show less' : 'Show more',
                style: const TextStyle(color: sapphoInfo),
              ),
            ),
        ],
      ),
    );
  }
}

/// Metadata section
class _MetadataSection extends StatelessWidget {
  final Audiobook book;
  final Function(String)? onAuthorClick;
  final Function(String)? onSeriesClick;
  final Function(String)? onGenreClick;

  const _MetadataSection({
    required this.book,
    this.onAuthorClick,
    this.onSeriesClick,
    this.onGenreClick,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (book.author != null)
            _MetadataItem(
              label: 'Author',
              value: book.author!,
              isClickable: onAuthorClick != null,
              onTap: () => onAuthorClick?.call(book.author!),
            ),
          if (book.narrator != null)
            _MetadataItem(label: 'Narrator', value: book.narrator!),
          if (book.series != null)
            _MetadataItem(
              label: 'Series',
              value: book.seriesPosition != null
                  ? '${book.series} (Book ${book.formattedSeriesPosition})'
                  : book.series!,
              isClickable: onSeriesClick != null,
              onTap: () => onSeriesClick?.call(book.series!),
            ),
          if (book.normalizedGenre != null || book.genre != null)
            _MetadataItem(
              label: 'Genre',
              value: book.normalizedGenre ?? book.genre!,
              isClickable: onGenreClick != null,
              onTap: () =>
                  onGenreClick?.call(book.normalizedGenre ?? book.genre!),
            ),
          if (book.publishYear != null)
            _MetadataItem(
              label: 'Published',
              value: book.publishYear.toString(),
            ),
          if (book.duration != null)
            _MetadataItem(
              label: 'Duration',
              value: _formatDuration(book.duration!),
            ),
        ],
      ),
    );
  }

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    if (hours > 0) {
      return '${hours}h ${minutes}m';
    }
    return '${minutes}m';
  }
}

/// Single metadata item
class _MetadataItem extends StatelessWidget {
  final String label;
  final String value;
  final bool isClickable;
  final VoidCallback? onTap;

  const _MetadataItem({
    required this.label,
    required this.value,
    this.isClickable = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: const TextStyle(fontSize: 14, color: sapphoIconDefault),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: isClickable ? onTap : null,
              child: Text(
                value,
                style: TextStyle(
                  fontSize: 14,
                  color: isClickable ? sapphoInfo : Colors.white,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Chapters bottom sheet
class _ChaptersBottomSheet extends StatefulWidget {
  final List<Chapter> chapters;
  final int? currentPosition;

  const _ChaptersBottomSheet({required this.chapters, this.currentPosition});

  @override
  State<_ChaptersBottomSheet> createState() => _ChaptersBottomSheetState();
}

class _ChaptersBottomSheetState extends State<_ChaptersBottomSheet> {
  bool _hasScrolledToCurrentChapter = false;

  int _findCurrentChapterIndex() {
    if (widget.currentPosition == null) return -1;
    for (int i = 0; i < widget.chapters.length; i++) {
      final chapter = widget.chapters[i];
      final endTime =
          chapter.endTime ?? chapter.startTime + (chapter.duration ?? 0);
      if (widget.currentPosition! >= chapter.startTime &&
          widget.currentPosition! < endTime) {
        return i;
      }
    }
    return -1;
  }

  bool _isCurrentChapter(Chapter chapter) {
    if (widget.currentPosition == null) return false;
    final endTime =
        chapter.endTime ?? chapter.startTime + (chapter.duration ?? 0);
    return widget.currentPosition! >= chapter.startTime &&
        widget.currentPosition! < endTime;
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) {
        // Scroll to current chapter after build
        if (!_hasScrolledToCurrentChapter) {
          _hasScrolledToCurrentChapter = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (scrollController.hasClients) {
              final currentIndex = _findCurrentChapterIndex();
              if (currentIndex >= 0 && currentIndex > 2) {
                // Each chapter item is approximately 52 pixels (44 content + 8 margin)
                const itemHeight = 52.0;
                // Position current chapter near the top with 1-2 items above
                final targetOffset = (currentIndex - 2) * itemHeight;
                final maxScroll = scrollController.position.maxScrollExtent;
                scrollController.animateTo(
                  targetOffset.clamp(0.0, maxScroll),
                  duration: const Duration(milliseconds: 300),
                  curve: Curves.easeOut,
                );
              }
            }
          });
        }

        return Column(
          children: [
            // Handle
            Container(
              margin: const EdgeInsets.symmetric(vertical: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            // Title
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Chapters (${widget.chapters.length})',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                      color: sapphoText,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: sapphoIconDefault),
                  ),
                ],
              ),
            ),
            const Divider(color: sapphoSurfaceBorder),
            // Chapters list
            Expanded(
              child: ListView.builder(
                controller: scrollController,
                padding: EdgeInsets.fromLTRB(16, 8, 16, bottomPadding + 16),
                itemCount: widget.chapters.length,
                itemBuilder: (context, index) {
                  final chapter = widget.chapters[index];
                  final isCurrentChapter = _isCurrentChapter(chapter);
                  return _ChapterItem(
                    chapter: chapter,
                    isCurrentChapter: isCurrentChapter,
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

/// Chapter item
class _ChapterItem extends StatelessWidget {
  final Chapter chapter;
  final bool isCurrentChapter;

  const _ChapterItem({required this.chapter, this.isCurrentChapter = false});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isCurrentChapter
            ? sapphoInfo.withValues(alpha: 0.1)
            : sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(8),
        border: isCurrentChapter
            ? Border.all(color: sapphoInfo.withValues(alpha: 0.3))
            : null,
      ),
      child: Row(
        children: [
          if (isCurrentChapter)
            const Padding(
              padding: EdgeInsets.only(right: 8),
              child: Icon(Icons.play_arrow, size: 16, color: sapphoInfo),
            ),
          Expanded(
            child: Text(
              chapter.title ?? 'Chapter',
              style: TextStyle(
                fontSize: 14,
                color: isCurrentChapter ? sapphoInfo : sapphoText,
                fontWeight: isCurrentChapter
                    ? FontWeight.w600
                    : FontWeight.normal,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Text(
            _formatDuration(chapter.duration?.toInt() ?? 0),
            style: const TextStyle(fontSize: 12, color: sapphoTextMuted),
          ),
        ],
      ),
    );
  }

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    final secs = seconds % 60;
    if (hours > 0) {
      return '${hours}:${minutes.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
    }
    return '${minutes}:${secs.toString().padLeft(2, '0')}';
  }
}

/// Files dropdown section
class _FilesDropdown extends StatefulWidget {
  final List<DirectoryFile> files;

  const _FilesDropdown({required this.files});

  @override
  State<_FilesDropdown> createState() => _FilesDropdownState();
}

class _FilesDropdownState extends State<_FilesDropdown> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        children: [
          // Toggle button
          GestureDetector(
            onTap: () => setState(() => _expanded = !_expanded),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: sapphoSurfaceLight.withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      const Icon(
                        Icons.description,
                        size: 20,
                        color: sapphoText,
                      ),
                      const SizedBox(width: 12),
                      Text(
                        '${widget.files.length} File${widget.files.length != 1 ? 's' : ''}',
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color: sapphoText,
                        ),
                      ),
                    ],
                  ),
                  Icon(
                    _expanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    size: 20,
                    color: sapphoText,
                  ),
                ],
              ),
            ),
          ),

          // Files list (expanded)
          if (_expanded) ...[
            const SizedBox(height: 8),
            ...widget.files.map(
              (file) => Container(
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 4),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: sapphoSurfaceLight.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      file.name,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: sapphoText,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      file.formattedSize,
                      style: const TextStyle(
                        fontSize: 12,
                        color: sapphoIconDefault,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// AI Recap Dialog - "Catch Me Up" feature
class _RecapDialog extends StatelessWidget {
  final DetailProvider detail;

  const _RecapDialog({required this.detail});

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: sapphoSurface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500, maxHeight: 600),
        child: ListenableBuilder(
          listenable: detail,
          builder: (context, _) {
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Header
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: const BoxDecoration(
                    border: Border(
                      bottom: BorderSide(color: sapphoSurfaceBorder),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.auto_awesome,
                        color: sapphoFeatureAccent,
                        size: 24,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Catch Me Up',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w600,
                                color: sapphoText,
                              ),
                            ),
                            if (detail.recap?.cached == true)
                              const Text(
                                'From cache',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: sapphoTextMuted,
                                ),
                              ),
                          ],
                        ),
                      ),
                      IconButton(
                        onPressed: () {
                          detail.dismissRecap();
                          Navigator.pop(context);
                        },
                        icon: const Icon(Icons.close, color: sapphoIconDefault),
                      ),
                    ],
                  ),
                ),

                // Content
                Flexible(child: _buildContent(context)),

                // Footer actions
                if (!detail.isLoadingRecap && detail.recap != null)
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: const BoxDecoration(
                      border: Border(
                        top: BorderSide(color: sapphoSurfaceBorder),
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        TextButton.icon(
                          onPressed: () => detail.regenerateRecap(),
                          icon: const Icon(Icons.refresh, size: 16),
                          label: const Text('Regenerate'),
                          style: TextButton.styleFrom(
                            foregroundColor: sapphoIconDefault,
                          ),
                        ),
                        const SizedBox(width: 8),
                        ElevatedButton(
                          onPressed: () {
                            detail.dismissRecap();
                            Navigator.pop(context);
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: sapphoInfo,
                            foregroundColor: Colors.white,
                          ),
                          child: const Text('Got it!'),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    if (detail.isLoadingRecap) {
      return Padding(
        padding: const EdgeInsets.all(48),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: sapphoFeatureAccent),
            const SizedBox(height: 24),
            Text(
              'Generating your recap...',
              style: TextStyle(fontSize: 16, color: sapphoText),
            ),
            const SizedBox(height: 8),
            Text(
              'This may take a moment',
              style: TextStyle(fontSize: 14, color: sapphoTextMuted),
            ),
          ],
        ),
      );
    }

    if (detail.recapError != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, color: sapphoError, size: 48),
            const SizedBox(height: 16),
            const Text(
              'Failed to generate recap',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: sapphoText,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              detail.recapError!,
              style: const TextStyle(fontSize: 14, color: sapphoTextMuted),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () => detail.loadRecap(),
              icon: const Icon(Icons.refresh),
              label: const Text('Try Again'),
              style: ElevatedButton.styleFrom(
                backgroundColor: sapphoInfo,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      );
    }

    if (detail.recap == null) {
      return const SizedBox.shrink();
    }

    final recap = detail.recap!;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Books included section
          if (recap.booksIncluded != null &&
              recap.booksIncluded!.isNotEmpty) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: sapphoInfo.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: sapphoInfo.withValues(alpha: 0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.menu_book, size: 16, color: sapphoInfo),
                      SizedBox(width: 8),
                      Text(
                        'Books included in this recap:',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: sapphoInfo,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ...recap.booksIncluded!.map(
                    (book) => Padding(
                      padding: const EdgeInsets.only(left: 24, bottom: 4),
                      child: Text(
                        book.seriesPosition != null
                            ? '${book.seriesPosition!.toStringAsFixed(book.seriesPosition! % 1 == 0 ? 0 : 1)}. ${book.title}'
                            : '• ${book.title}',
                        style: const TextStyle(fontSize: 13, color: sapphoText),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Previous book note
          if (recap.previousBookTitle != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: sapphoFeatureAccent.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.bookmark,
                    size: 16,
                    color: sapphoFeatureAccent,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Recap from: ${recap.previousBookTitle}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: sapphoFeatureAccent,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Recap text
          Text(
            recap.recap,
            style: const TextStyle(
              fontSize: 15,
              color: sapphoTextLight,
              height: 1.6,
            ),
          ),
        ],
      ),
    );
  }
}

/// Edit metadata bottom sheet with lookup and embed functionality
class _EditMetadataSheet extends StatefulWidget {
  final Audiobook audiobook;
  final DetailProvider detail;

  const _EditMetadataSheet({required this.audiobook, required this.detail});

  @override
  State<_EditMetadataSheet> createState() => _EditMetadataSheetState();
}

class _EditMetadataSheetState extends State<_EditMetadataSheet> {
  late TextEditingController _titleController;
  late TextEditingController _subtitleController;
  late TextEditingController _authorController;
  late TextEditingController _narratorController;
  late TextEditingController _seriesController;
  late TextEditingController _seriesPositionController;
  late TextEditingController _genreController;
  late TextEditingController _tagsController;
  late TextEditingController _publishedYearController;
  late TextEditingController _publisherController;
  late TextEditingController _descriptionController;
  late TextEditingController _isbnController;
  late TextEditingController _asinController;
  late TextEditingController _languageController;

  // Search controllers
  late TextEditingController _searchTitleController;
  late TextEditingController _searchAuthorController;

  bool _showSearchResults = false;

  @override
  void initState() {
    super.initState();
    final book = widget.audiobook;
    _titleController = TextEditingController(text: book.title);
    _subtitleController = TextEditingController(text: book.subtitle ?? '');
    _authorController = TextEditingController(text: book.author ?? '');
    _narratorController = TextEditingController(text: book.narrator ?? '');
    _seriesController = TextEditingController(text: book.series ?? '');
    _seriesPositionController = TextEditingController(
      text: book.seriesPosition?.toString() ?? '',
    );
    _genreController = TextEditingController(text: book.genre ?? '');
    _tagsController = TextEditingController(text: book.tags ?? '');
    _publishedYearController = TextEditingController(
      text: book.publishYear?.toString() ?? '',
    );
    _publisherController = TextEditingController(text: book.publisher ?? '');
    _descriptionController = TextEditingController(
      text: book.description ?? '',
    );
    _isbnController = TextEditingController(text: book.isbn ?? '');
    _asinController = TextEditingController(text: book.asin ?? '');
    _asinController.addListener(_onAsinChanged);
    _languageController = TextEditingController(text: book.language ?? '');

    // Initialize search fields with current values
    _searchTitleController = TextEditingController(text: book.title);
    _searchAuthorController = TextEditingController(text: book.author ?? '');
  }

  @override
  void dispose() {
    _titleController.dispose();
    _subtitleController.dispose();
    _authorController.dispose();
    _narratorController.dispose();
    _seriesController.dispose();
    _seriesPositionController.dispose();
    _genreController.dispose();
    _tagsController.dispose();
    _publishedYearController.dispose();
    _publisherController.dispose();
    _descriptionController.dispose();
    _isbnController.dispose();
    _asinController.removeListener(_onAsinChanged);
    _asinController.dispose();
    _languageController.dispose();
    _searchTitleController.dispose();
    _searchAuthorController.dispose();
    widget.detail.clearMetadataSearch();
    super.dispose();
  }

  void _onAsinChanged() {
    // Trigger rebuild to update Fetch Chapters button state
    setState(() {});
  }

  AudiobookUpdateRequest _buildRequest() {
    return AudiobookUpdateRequest(
      title: _titleController.text.isNotEmpty ? _titleController.text : null,
      subtitle: _subtitleController.text.isNotEmpty
          ? _subtitleController.text
          : null,
      author: _authorController.text.isNotEmpty ? _authorController.text : null,
      narrator: _narratorController.text.isNotEmpty
          ? _narratorController.text
          : null,
      series: _seriesController.text.isNotEmpty ? _seriesController.text : null,
      seriesPosition: double.tryParse(_seriesPositionController.text),
      genre: _genreController.text.isNotEmpty ? _genreController.text : null,
      tags: _tagsController.text.isNotEmpty ? _tagsController.text : null,
      publishedYear: int.tryParse(_publishedYearController.text),
      publisher: _publisherController.text.isNotEmpty
          ? _publisherController.text
          : null,
      description: _descriptionController.text.isNotEmpty
          ? _descriptionController.text
          : null,
      isbn: _isbnController.text.isNotEmpty ? _isbnController.text : null,
      asin: _asinController.text.isNotEmpty ? _asinController.text : null,
      language: _languageController.text.isNotEmpty
          ? _languageController.text
          : null,
      coverUrl: _selectedCoverUrl,
    );
  }

  void _save({bool embed = false}) async {
    final request = _buildRequest();
    // Capture scaffold messenger before popping (context becomes invalid after pop)
    final messenger = ScaffoldMessenger.of(context);
    Navigator.pop(context);

    try {
      await widget.detail.saveMetadata(request, embed: embed);

      // Check embed result
      final embedResult = widget.detail.embedMetadataResult;
      String message;
      if (embed) {
        if (embedResult != null && embedResult.startsWith('Error')) {
          message = 'Metadata saved. Embed failed: $embedResult';
        } else {
          message = 'Metadata saved and embedded';
        }
      } else {
        message = 'Metadata saved';
      }

      messenger.showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Failed: $e')));
    }
  }

  void _searchMetadata() {
    widget.detail.searchMetadata(
      title: _searchTitleController.text,
      author: _searchAuthorController.text,
    );
    setState(() => _showSearchResults = true);
  }

  String? _selectedCoverUrl; // Track if cover should be applied

  void _applySearchResult(MetadataSearchResult result) {
    // Show comparison dialog
    showDialog(
      context: context,
      builder: (ctx) => _MetadataComparisonDialog(
        result: result,
        currentTitle: _titleController.text,
        currentSubtitle: _subtitleController.text,
        currentAuthor: _authorController.text,
        currentNarrator: _narratorController.text,
        currentSeries: _seriesController.text,
        currentSeriesPosition: _seriesPositionController.text,
        currentGenre: _genreController.text,
        currentTags: _tagsController.text,
        currentPublishedYear: _publishedYearController.text,
        currentPublisher: _publisherController.text,
        currentDescription: _descriptionController.text,
        currentIsbn: _isbnController.text,
        currentAsin: _asinController.text,
        currentLanguage: _languageController.text,
        currentCoverUrl:
            widget.detail.serverUrl != null && widget.audiobook.id > 0
            ? '${widget.detail.serverUrl}/api/audiobooks/${widget.audiobook.id}/cover'
            : null,
        onApply: (selectedFields, applyCover) {
          setState(() {
            if (selectedFields['title'] == true && result.title != null) {
              _titleController.text = result.title!;
            }
            if (selectedFields['subtitle'] == true && result.subtitle != null) {
              _subtitleController.text = result.subtitle!;
            }
            if (selectedFields['author'] == true && result.author != null) {
              _authorController.text = result.author!;
            }
            if (selectedFields['narrator'] == true && result.narrator != null) {
              _narratorController.text = result.narrator!;
            }
            if (selectedFields['series'] == true && result.series != null) {
              _seriesController.text = result.series!;
            }
            if (selectedFields['seriesPosition'] == true &&
                result.seriesPosition != null) {
              _seriesPositionController.text = result.seriesPosition.toString();
            }
            if (selectedFields['genre'] == true && result.genre != null) {
              _genreController.text = result.genre!;
            }
            if (selectedFields['tags'] == true && result.tags != null) {
              _tagsController.text = result.tags!;
            }
            if (selectedFields['publishedYear'] == true &&
                result.publishedYear != null) {
              _publishedYearController.text = result.publishedYear.toString();
            }
            if (selectedFields['publisher'] == true &&
                result.publisher != null) {
              _publisherController.text = result.publisher!;
            }
            if (selectedFields['description'] == true &&
                result.description != null) {
              _descriptionController.text = result.description!;
            }
            if (selectedFields['isbn'] == true && result.isbn != null) {
              _isbnController.text = result.isbn!;
            }
            if (selectedFields['asin'] == true && result.asin != null) {
              _asinController.text = result.asin!;
            }
            if (selectedFields['language'] == true && result.language != null) {
              _languageController.text = result.language!;
            }
            // Store cover URL if selected
            _selectedCoverUrl = applyCover ? result.image : null;
            _showSearchResults = false;
          });
          widget.detail.clearMetadataSearch();
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).viewInsets.bottom;

    return Container(
      height: MediaQuery.of(context).size.height * 0.9,
      decoration: const BoxDecoration(
        color: sapphoSurface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: sapphoProgressTrack,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text(
                    'Cancel',
                    style: TextStyle(color: sapphoTextMuted),
                  ),
                ),
                const Text(
                  'Edit Metadata',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: sapphoText,
                  ),
                ),
                const SizedBox(width: 60), // Balance the header
              ],
            ),
          ),

          const Divider(color: sapphoSurfaceBorder),

          // Form fields
          Expanded(
            child: ListenableBuilder(
              listenable: widget.detail,
              builder: (context, _) {
                return ListView(
                  padding: EdgeInsets.fromLTRB(16, 8, 16, bottomPadding + 16),
                  children: [
                    // Lookup Metadata section
                    Container(
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.only(bottom: 16),
                      decoration: BoxDecoration(
                        color: sapphoInfo.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: sapphoInfo.withValues(alpha: 0.2),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.search, size: 18, color: sapphoInfo),
                              SizedBox(width: 8),
                              Text(
                                'Lookup Metadata',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: sapphoInfo,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Expanded(
                                child: TextField(
                                  controller: _searchTitleController,
                                  style: const TextStyle(
                                    color: sapphoText,
                                    fontSize: 13,
                                  ),
                                  decoration: InputDecoration(
                                    hintText: 'Title',
                                    hintStyle: TextStyle(
                                      color: sapphoTextMuted.withValues(
                                        alpha: 0.5,
                                      ),
                                    ),
                                    filled: true,
                                    fillColor: sapphoSurfaceLight,
                                    contentPadding: const EdgeInsets.symmetric(
                                      horizontal: 10,
                                      vertical: 8,
                                    ),
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(6),
                                      borderSide: BorderSide.none,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: TextField(
                                  controller: _searchAuthorController,
                                  style: const TextStyle(
                                    color: sapphoText,
                                    fontSize: 13,
                                  ),
                                  decoration: InputDecoration(
                                    hintText: 'Author',
                                    hintStyle: TextStyle(
                                      color: sapphoTextMuted.withValues(
                                        alpha: 0.5,
                                      ),
                                    ),
                                    filled: true,
                                    fillColor: sapphoSurfaceLight,
                                    contentPadding: const EdgeInsets.symmetric(
                                      horizontal: 10,
                                      vertical: 8,
                                    ),
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(6),
                                      borderSide: BorderSide.none,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          SizedBox(
                            width: double.infinity,
                            child: ElevatedButton.icon(
                              onPressed: widget.detail.isSearchingMetadata
                                  ? null
                                  : _searchMetadata,
                              icon: widget.detail.isSearchingMetadata
                                  ? const SizedBox(
                                      width: 16,
                                      height: 16,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Icon(Icons.search, size: 18),
                              label: Text(
                                widget.detail.isSearchingMetadata
                                    ? 'Searching...'
                                    : 'Search',
                              ),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: sapphoInfo,
                                foregroundColor: Colors.white,
                                padding: const EdgeInsets.symmetric(
                                  vertical: 10,
                                ),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(8),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                    // Search results
                    if (_showSearchResults) ...[
                      if (widget.detail.metadataSearchError != null)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: Text(
                            widget.detail.metadataSearchError!,
                            style: const TextStyle(
                              color: sapphoError,
                              fontSize: 13,
                            ),
                          ),
                        ),
                      if (widget.detail.metadataSearchResults.isNotEmpty) ...[
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '${widget.detail.metadataSearchResults.length} results',
                              style: const TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 13,
                              ),
                            ),
                            TextButton(
                              onPressed: () {
                                setState(() => _showSearchResults = false);
                                widget.detail.clearMetadataSearch();
                              },
                              child: const Text(
                                'Hide',
                                style: TextStyle(fontSize: 13),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        ...widget.detail.metadataSearchResults.map(
                          (result) => _MetadataResultItem(
                            result: result,
                            onApply: () => _applySearchResult(result),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                    ],

                    // Form fields
                    _buildTextField('Title', _titleController),
                    _buildTextField('Subtitle', _subtitleController),
                    _buildTextField('Author', _authorController),
                    _buildTextField('Narrator', _narratorController),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField('Series', _seriesController),
                        ),
                        const SizedBox(width: 12),
                        SizedBox(
                          width: 80,
                          child: _buildTextField(
                            'Position',
                            _seriesPositionController,
                            keyboardType: TextInputType.number,
                          ),
                        ),
                      ],
                    ),
                    _buildTextField('Genre', _genreController),
                    _buildTextField(
                      'Tags',
                      _tagsController,
                      hint: 'Comma-separated',
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField(
                            'Published Year',
                            _publishedYearController,
                            keyboardType: TextInputType.number,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: _buildTextField(
                            'Publisher',
                            _publisherController,
                          ),
                        ),
                      ],
                    ),
                    _buildTextField(
                      'Description',
                      _descriptionController,
                      maxLines: 4,
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField('ISBN', _isbnController),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Row(
                            children: [
                              Expanded(
                                child: _buildTextField('ASIN', _asinController),
                              ),
                              const SizedBox(width: 8),
                              SizedBox(
                                height: 48,
                                child: ElevatedButton(
                                  onPressed:
                                      widget.detail.isFetchingChapters ||
                                          _asinController.text.isEmpty ||
                                          !RegExp(
                                            r'^[A-Z0-9]{10}$',
                                            caseSensitive: false,
                                          ).hasMatch(_asinController.text)
                                      ? null
                                      : () => widget.detail
                                            .fetchChaptersFromAudnexus(
                                              _asinController.text,
                                            ),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: sapphoFeatureAccent,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                  ),
                                  child: widget.detail.isFetchingChapters
                                      ? const SizedBox(
                                          width: 16,
                                          height: 16,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                            color: Colors.white,
                                          ),
                                        )
                                      : const Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            Icon(Icons.list, size: 16),
                                            SizedBox(width: 4),
                                            Text(
                                              'Chapters',
                                              style: TextStyle(fontSize: 12),
                                            ),
                                          ],
                                        ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    // Fetch chapters result message
                    if (widget.detail.fetchChaptersResult != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color:
                                widget.detail.fetchChaptersResult!
                                        .toLowerCase()
                                        .contains('error') ||
                                    widget.detail.fetchChaptersResult!
                                        .toLowerCase()
                                        .contains('not found')
                                ? sapphoError.withValues(alpha: 0.15)
                                : sapphoSuccess.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                widget.detail.fetchChaptersResult!
                                            .toLowerCase()
                                            .contains('error') ||
                                        widget.detail.fetchChaptersResult!
                                            .toLowerCase()
                                            .contains('not found')
                                    ? Icons.error_outline
                                    : Icons.check_circle_outline,
                                size: 16,
                                color:
                                    widget.detail.fetchChaptersResult!
                                            .toLowerCase()
                                            .contains('error') ||
                                        widget.detail.fetchChaptersResult!
                                            .toLowerCase()
                                            .contains('not found')
                                    ? sapphoError
                                    : sapphoSuccess,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  widget.detail.fetchChaptersResult!,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color:
                                        widget.detail.fetchChaptersResult!
                                                .toLowerCase()
                                                .contains('error') ||
                                            widget.detail.fetchChaptersResult!
                                                .toLowerCase()
                                                .contains('not found')
                                        ? sapphoError
                                        : sapphoSuccess,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    _buildTextField('Language', _languageController),

                    const SizedBox(height: 24),

                    // Action buttons
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: widget.detail.isSavingMetadata
                                ? null
                                : () => _save(embed: false),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: sapphoInfo,
                              side: const BorderSide(color: sapphoInfo),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            child: const Text(
                              'Save',
                              style: TextStyle(fontWeight: FontWeight.w600),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: widget.detail.isSavingMetadata
                                ? null
                                : () => _save(embed: true),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: sapphoSuccess,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            child:
                                widget.detail.isSavingMetadata ||
                                    widget.detail.isEmbeddingMetadata
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: Colors.white,
                                    ),
                                  )
                                : const Text(
                                    'Save & Embed',
                                    style: TextStyle(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                          ),
                        ),
                      ],
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTextField(
    String label,
    TextEditingController controller, {
    int maxLines = 1,
    TextInputType? keyboardType,
    String? hint,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: sapphoTextMuted,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 4),
          TextField(
            controller: controller,
            maxLines: maxLines,
            keyboardType: keyboardType,
            style: const TextStyle(color: sapphoText, fontSize: 14),
            decoration: InputDecoration(
              hintText: hint,
              hintStyle: TextStyle(
                color: sapphoTextMuted.withValues(alpha: 0.5),
              ),
              filled: true,
              fillColor: sapphoSurfaceLight,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 10,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide.none,
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(
                  color: Colors.white.withValues(alpha: 0.1),
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: sapphoInfo),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Metadata search result item
class _MetadataResultItem extends StatelessWidget {
  final MetadataSearchResult result;
  final VoidCallback onApply;

  const _MetadataResultItem({required this.result, required this.onApply});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
      ),
      child: InkWell(
        onTap: onApply,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Cover image
              if (result.image != null)
                Container(
                  width: 50,
                  height: 50,
                  margin: const EdgeInsets.only(right: 12),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(4),
                    color: sapphoProgressTrack,
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: CachedNetworkImage(
                    imageUrl: result.image!,
                    fit: BoxFit.cover,
                    memCacheWidth: 100,
                    memCacheHeight: 100,
                    fadeInDuration: Duration.zero,
                    placeholder: (_, __) => const Center(
                      child: Icon(Icons.book, color: sapphoTextMuted, size: 20),
                    ),
                    errorWidget: (_, __, ___) => const Center(
                      child: Icon(Icons.book, color: sapphoTextMuted, size: 20),
                    ),
                  ),
                ),
              // Info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            result.title ?? 'Unknown',
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: sapphoText,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: _getSourceColor(
                              result.source,
                            ).withValues(alpha: 0.2),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            result.source,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w500,
                              color: _getSourceColor(result.source),
                            ),
                          ),
                        ),
                        if (result.hasChapters == true) ...[
                          const SizedBox(width: 4),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 4,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: sapphoFeatureAccent.withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: const Text(
                              'Ch',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w500,
                                color: sapphoFeatureAccent,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    if (result.author != null)
                      Text(
                        result.author!,
                        style: const TextStyle(
                          fontSize: 12,
                          color: sapphoTextMuted,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    if (result.narrator != null)
                      Text(
                        'Narrated by ${result.narrator}',
                        style: const TextStyle(
                          fontSize: 11,
                          color: sapphoIconDefault,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    if (result.series != null)
                      Text(
                        result.seriesPosition != null
                            ? '${result.series} #${result.seriesPosition}'
                            : result.series!,
                        style: const TextStyle(fontSize: 11, color: sapphoInfo),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                  ],
                ),
              ),
              const Icon(
                Icons.chevron_right,
                color: sapphoIconDefault,
                size: 20,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Color _getSourceColor(String source) {
    switch (source.toLowerCase()) {
      case 'audible':
        return sapphoWarning;
      case 'google':
        return sapphoInfo;
      default:
        return sapphoTextMuted;
    }
  }
}

/// Dialog to compare and select which metadata fields to apply
class _MetadataComparisonDialog extends StatefulWidget {
  final MetadataSearchResult result;
  final String currentTitle;
  final String currentSubtitle;
  final String currentAuthor;
  final String currentNarrator;
  final String currentSeries;
  final String currentSeriesPosition;
  final String currentGenre;
  final String currentTags;
  final String currentPublishedYear;
  final String currentPublisher;
  final String currentDescription;
  final String currentIsbn;
  final String currentAsin;
  final String currentLanguage;
  final String? currentCoverUrl;
  final Function(Map<String, bool>, bool applyCover) onApply;

  const _MetadataComparisonDialog({
    required this.result,
    required this.currentTitle,
    required this.currentSubtitle,
    required this.currentAuthor,
    required this.currentNarrator,
    required this.currentSeries,
    required this.currentSeriesPosition,
    required this.currentGenre,
    required this.currentTags,
    required this.currentPublishedYear,
    required this.currentPublisher,
    required this.currentDescription,
    required this.currentIsbn,
    required this.currentAsin,
    required this.currentLanguage,
    required this.currentCoverUrl,
    required this.onApply,
  });

  @override
  State<_MetadataComparisonDialog> createState() =>
      _MetadataComparisonDialogState();
}

class _MetadataComparisonDialogState extends State<_MetadataComparisonDialog> {
  final Map<String, bool> _selectedFields = {};
  bool _applyCover = false;

  @override
  void initState() {
    super.initState();
    // Pre-select fields that have new values different from current
    _initField('title', widget.currentTitle, widget.result.title);
    _initField('subtitle', widget.currentSubtitle, widget.result.subtitle);
    _initField('author', widget.currentAuthor, widget.result.author);
    _initField('narrator', widget.currentNarrator, widget.result.narrator);
    _initField('series', widget.currentSeries, widget.result.series);
    _initField(
      'seriesPosition',
      widget.currentSeriesPosition,
      widget.result.seriesPosition?.toString(),
    );
    _initField('genre', widget.currentGenre, widget.result.genre);
    _initField('tags', widget.currentTags, widget.result.tags);
    _initField(
      'publishedYear',
      widget.currentPublishedYear,
      widget.result.publishedYear?.toString(),
    );
    _initField('publisher', widget.currentPublisher, widget.result.publisher);
    _initField(
      'description',
      widget.currentDescription,
      widget.result.description,
    );
    _initField('isbn', widget.currentIsbn, widget.result.isbn);
    _initField('asin', widget.currentAsin, widget.result.asin);
    _initField('language', widget.currentLanguage, widget.result.language);
    // Pre-select cover if available
    if (widget.result.image != null && widget.result.image!.isNotEmpty) {
      _applyCover = true;
    }
  }

  void _initField(String key, String current, String? newValue) {
    if (newValue != null && newValue.isNotEmpty && newValue != current) {
      _selectedFields[key] = true;
    }
  }

  List<_FieldComparison> _getChangedFields() {
    final fields = <_FieldComparison>[];

    void addIfChanged(
      String key,
      String label,
      String current,
      String? newValue,
    ) {
      if (newValue != null && newValue.isNotEmpty) {
        final isDifferent = newValue != current;
        fields.add(
          _FieldComparison(
            key: key,
            label: label,
            currentValue: current.isEmpty ? '(empty)' : current,
            newValue: newValue,
            isDifferent: isDifferent,
          ),
        );
      }
    }

    addIfChanged('title', 'Title', widget.currentTitle, widget.result.title);
    addIfChanged(
      'subtitle',
      'Subtitle',
      widget.currentSubtitle,
      widget.result.subtitle,
    );
    addIfChanged(
      'author',
      'Author',
      widget.currentAuthor,
      widget.result.author,
    );
    addIfChanged(
      'narrator',
      'Narrator',
      widget.currentNarrator,
      widget.result.narrator,
    );
    addIfChanged(
      'series',
      'Series',
      widget.currentSeries,
      widget.result.series,
    );
    addIfChanged(
      'seriesPosition',
      'Series #',
      widget.currentSeriesPosition,
      widget.result.seriesPosition?.toString(),
    );
    addIfChanged('genre', 'Genre', widget.currentGenre, widget.result.genre);
    addIfChanged('tags', 'Tags', widget.currentTags, widget.result.tags);
    addIfChanged(
      'publishedYear',
      'Year',
      widget.currentPublishedYear,
      widget.result.publishedYear?.toString(),
    );
    addIfChanged(
      'publisher',
      'Publisher',
      widget.currentPublisher,
      widget.result.publisher,
    );
    addIfChanged(
      'description',
      'Description',
      widget.currentDescription,
      widget.result.description,
    );
    addIfChanged('isbn', 'ISBN', widget.currentIsbn, widget.result.isbn);
    addIfChanged('asin', 'ASIN', widget.currentAsin, widget.result.asin);
    addIfChanged(
      'language',
      'Language',
      widget.currentLanguage,
      widget.result.language,
    );

    return fields;
  }

  @override
  Widget build(BuildContext context) {
    final fields = _getChangedFields();
    final changedCount =
        fields.where((f) => f.isDifferent).length +
        (widget.result.image != null ? 1 : 0);
    final selectedCount =
        _selectedFields.values.where((v) => v).length + (_applyCover ? 1 : 0);
    final hasCover =
        widget.result.image != null && widget.result.image!.isNotEmpty;

    return Dialog(
      backgroundColor: sapphoSurface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 500,
          maxHeight: MediaQuery.of(context).size.height * 0.8,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                border: Border(bottom: BorderSide(color: sapphoSurfaceBorder)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Apply Metadata',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            color: sapphoText,
                          ),
                        ),
                        Text(
                          '$changedCount changes available',
                          style: const TextStyle(
                            fontSize: 12,
                            color: sapphoTextMuted,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: sapphoIconDefault),
                  ),
                ],
              ),
            ),

            // Cover image comparison
            if (hasCover)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(
                  border: Border(
                    bottom: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
                child: InkWell(
                  onTap: () => setState(() => _applyCover = !_applyCover),
                  child: Row(
                    children: [
                      // Checkbox
                      Container(
                        width: 22,
                        height: 22,
                        margin: const EdgeInsets.only(right: 12),
                        decoration: BoxDecoration(
                          color: _applyCover ? sapphoInfo : Colors.transparent,
                          borderRadius: BorderRadius.circular(4),
                          border: Border.all(
                            color: _applyCover ? sapphoInfo : sapphoIconDefault,
                            width: 2,
                          ),
                        ),
                        child: _applyCover
                            ? const Icon(
                                Icons.check,
                                size: 16,
                                color: Colors.white,
                              )
                            : null,
                      ),
                      // Current cover
                      if (widget.currentCoverUrl != null)
                        Container(
                          width: 60,
                          height: 60,
                          margin: const EdgeInsets.only(right: 8),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(6),
                            color: sapphoProgressTrack,
                            border: Border.all(
                              color: sapphoError.withValues(alpha: 0.5),
                            ),
                          ),
                          clipBehavior: Clip.antiAlias,
                          child: Stack(
                            children: [
                              CachedNetworkImage(
                                imageUrl: widget.currentCoverUrl!,
                                fit: BoxFit.cover,
                                width: 60,
                                height: 60,
                                memCacheWidth: 120,
                                memCacheHeight: 120,
                                fadeInDuration: Duration.zero,
                                errorWidget: (_, __, ___) => const Icon(
                                  Icons.book,
                                  color: sapphoTextMuted,
                                ),
                              ),
                              Positioned(
                                bottom: 2,
                                left: 2,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 4,
                                    vertical: 1,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.black.withValues(alpha: 0.7),
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                  child: const Text(
                                    'Current',
                                    style: TextStyle(
                                      fontSize: 8,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      // Arrow
                      const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 8),
                        child: Icon(
                          Icons.arrow_forward,
                          size: 20,
                          color: sapphoInfo,
                        ),
                      ),
                      // New cover
                      Container(
                        width: 60,
                        height: 60,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(6),
                          color: sapphoProgressTrack,
                          border: Border.all(
                            color: sapphoSuccess.withValues(alpha: 0.5),
                          ),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Stack(
                          children: [
                            CachedNetworkImage(
                              imageUrl: widget.result.image!,
                              fit: BoxFit.cover,
                              width: 60,
                              height: 60,
                              memCacheWidth: 120,
                              memCacheHeight: 120,
                              fadeInDuration: Duration.zero,
                              errorWidget: (_, __, ___) => const Icon(
                                Icons.book,
                                color: sapphoTextMuted,
                              ),
                            ),
                            Positioned(
                              bottom: 2,
                              left: 2,
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                  vertical: 1,
                                ),
                                decoration: BoxDecoration(
                                  color: sapphoSuccess.withValues(alpha: 0.8),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                                child: const Text(
                                  'New',
                                  style: TextStyle(
                                    fontSize: 8,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Cover Image',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: sapphoInfo,
                              ),
                            ),
                            Text(
                              'Replace cover with new image',
                              style: TextStyle(
                                fontSize: 11,
                                color: sapphoTextMuted,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            // Select all / none
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  TextButton(
                    onPressed: () {
                      setState(() {
                        for (final field in fields.where(
                          (f) => f.isDifferent,
                        )) {
                          _selectedFields[field.key] = true;
                        }
                        if (hasCover) _applyCover = true;
                      });
                    },
                    child: const Text(
                      'Select All',
                      style: TextStyle(fontSize: 13),
                    ),
                  ),
                  TextButton(
                    onPressed: () {
                      setState(() {
                        _selectedFields.clear();
                        _applyCover = false;
                      });
                    },
                    child: const Text(
                      'Select None',
                      style: TextStyle(fontSize: 13, color: sapphoTextMuted),
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '$selectedCount selected',
                    style: const TextStyle(
                      fontSize: 12,
                      color: sapphoTextMuted,
                    ),
                  ),
                ],
              ),
            ),

            // Fields list
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: fields.length,
                itemBuilder: (context, index) {
                  final field = fields[index];
                  final isSelected = _selectedFields[field.key] == true;
                  final isEnabled = field.isDifferent;

                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? sapphoInfo.withValues(alpha: 0.1)
                          : sapphoSurfaceLight,
                      borderRadius: BorderRadius.circular(8),
                      border: isSelected
                          ? Border.all(color: sapphoInfo.withValues(alpha: 0.3))
                          : Border.all(
                              color: Colors.white.withValues(alpha: 0.05),
                            ),
                    ),
                    child: InkWell(
                      onTap: isEnabled
                          ? () {
                              setState(() {
                                _selectedFields[field.key] = !isSelected;
                              });
                            }
                          : null,
                      borderRadius: BorderRadius.circular(8),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            // Checkbox
                            Container(
                              width: 22,
                              height: 22,
                              margin: const EdgeInsets.only(right: 12, top: 2),
                              decoration: BoxDecoration(
                                color: isSelected
                                    ? sapphoInfo
                                    : Colors.transparent,
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(
                                  color: isEnabled
                                      ? (isSelected
                                            ? sapphoInfo
                                            : sapphoIconDefault)
                                      : sapphoProgressTrack,
                                  width: 2,
                                ),
                              ),
                              child: isSelected
                                  ? const Icon(
                                      Icons.check,
                                      size: 16,
                                      color: Colors.white,
                                    )
                                  : null,
                            ),
                            // Field info
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Text(
                                        field.label,
                                        style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                          color: isEnabled
                                              ? sapphoInfo
                                              : sapphoTextMuted,
                                        ),
                                      ),
                                      if (!field.isDifferent) ...[
                                        const SizedBox(width: 8),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 6,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: sapphoSuccess.withValues(
                                              alpha: 0.2,
                                            ),
                                            borderRadius: BorderRadius.circular(
                                              4,
                                            ),
                                          ),
                                          child: const Text(
                                            'Same',
                                            style: TextStyle(
                                              fontSize: 9,
                                              color: sapphoSuccess,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  // Current value
                                  if (field.currentValue.isNotEmpty &&
                                      field.currentValue != '(empty)')
                                    Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        const Icon(
                                          Icons.remove,
                                          size: 12,
                                          color: sapphoError,
                                        ),
                                        const SizedBox(width: 4),
                                        Expanded(
                                          child: Text(
                                            field.currentValue,
                                            style: TextStyle(
                                              fontSize: 12,
                                              color: field.isDifferent
                                                  ? sapphoError.withValues(
                                                      alpha: 0.8,
                                                    )
                                                  : sapphoTextMuted,
                                              decoration: field.isDifferent
                                                  ? TextDecoration.lineThrough
                                                  : null,
                                            ),
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ],
                                    ),
                                  // New value
                                  Row(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      const Icon(
                                        Icons.add,
                                        size: 12,
                                        color: sapphoSuccess,
                                      ),
                                      const SizedBox(width: 4),
                                      Expanded(
                                        child: Text(
                                          field.newValue,
                                          style: TextStyle(
                                            fontSize: 12,
                                            color: field.isDifferent
                                                ? sapphoSuccess
                                                : sapphoTextMuted,
                                          ),
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),

            // Footer
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                border: Border(top: BorderSide(color: sapphoSurfaceBorder)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: sapphoTextMuted,
                        side: const BorderSide(color: sapphoProgressTrack),
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                      child: const Text('Cancel'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: selectedCount > 0
                          ? () {
                              Navigator.pop(context);
                              widget.onApply(_selectedFields, _applyCover);
                            }
                          : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: sapphoInfo,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                      child: Text('Apply $selectedCount'),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FieldComparison {
  final String key;
  final String label;
  final String currentValue;
  final String newValue;
  final bool isDifferent;

  _FieldComparison({
    required this.key,
    required this.label,
    required this.currentValue,
    required this.newValue,
    required this.isDifferent,
  });
}
