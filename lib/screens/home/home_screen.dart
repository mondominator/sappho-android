import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:marquee/marquee.dart';
import '../../models/audiobook.dart';
import '../../providers/home_provider.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Home screen matching Android HomeScreen.kt
class HomeScreen extends StatefulWidget {
  final Function(int)? onAudiobookTap;
  final Function(int, int?)? onPlay;

  const HomeScreen({super.key, this.onAudiobookTap, this.onPlay});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  DateTime? _lastRefresh;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Only refresh if more than 5 minutes since last refresh
      final now = DateTime.now();
      if (_lastRefresh == null ||
          now.difference(_lastRefresh!).inMinutes >= 5) {
        _lastRefresh = now;
        context.read<HomeProvider>().refresh();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<HomeProvider>(
      builder: (context, home, child) {
        if (home.isLoading) {
          return const _SkeletonHomeScreen();
        }

        final bottomPadding = MediaQuery.of(context).padding.bottom;
        return RefreshIndicator(
          onRefresh: () => home.refresh(),
          color: sapphoInfo,
          backgroundColor: sapphoSurface,
          child: ListView(
            padding: EdgeInsets.fromLTRB(0, 16, 0, bottomPadding + 16),
            children: [
              // Continue Listening - Large cards (180dp)
              if (home.inProgress.isNotEmpty)
                _AudiobookSection(
                  title: 'Continue Listening',
                  books: home.inProgress,
                  serverUrl: home.serverUrl,
                  authToken: home.authToken,
                  onBookTap: widget.onAudiobookTap,
                  onToggleFavorite: (id) => home.toggleFavorite(id),
                  onPlay: widget.onPlay,
                  onMarkFinished: (id) => home.markFinished(id),
                  onClearProgress: (id) => home.clearProgress(id),
                  cardSize: 180,
                  titleSize: 20,
                ),

              // Up Next
              if (home.upNext.isNotEmpty)
                _AudiobookSection(
                  title: 'Up Next',
                  books: home.upNext,
                  serverUrl: home.serverUrl,
                  authToken: home.authToken,
                  onBookTap: widget.onAudiobookTap,
                  onToggleFavorite: (id) => home.toggleFavorite(id),
                  onPlay: widget.onPlay,
                  onMarkFinished: (id) => home.markFinished(id),
                  onClearProgress: (id) => home.clearProgress(id),
                ),

              // Recently Added
              if (home.recentlyAdded.isNotEmpty)
                _AudiobookSection(
                  title: 'Recently Added',
                  books: home.recentlyAdded,
                  serverUrl: home.serverUrl,
                  authToken: home.authToken,
                  onBookTap: widget.onAudiobookTap,
                  onToggleFavorite: (id) => home.toggleFavorite(id),
                  onPlay: widget.onPlay,
                  onMarkFinished: (id) => home.markFinished(id),
                  onClearProgress: (id) => home.clearProgress(id),
                ),

              // Listen Again - Smaller cards (120dp)
              if (home.finished.isNotEmpty)
                _AudiobookSection(
                  title: 'Listen Again',
                  books: home.finished,
                  serverUrl: home.serverUrl,
                  authToken: home.authToken,
                  onBookTap: widget.onAudiobookTap,
                  onToggleFavorite: (id) => home.toggleFavorite(id),
                  onPlay: widget.onPlay,
                  onMarkFinished: (id) => home.markFinished(id),
                  onClearProgress: (id) => home.clearProgress(id),
                  cardSize: 120,
                  titleSize: 14,
                ),

              // Empty state
              if (home.inProgress.isEmpty &&
                  home.upNext.isEmpty &&
                  home.recentlyAdded.isEmpty &&
                  home.finished.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(
                    child: Text(
                      'No audiobooks found.\nAdd some to your server to get started!',
                      style: TextStyle(color: sapphoIconDefault),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class _AudiobookSection extends StatelessWidget {
  final String title;
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final Function(int)? onBookTap;
  final Function(int)? onToggleFavorite;
  final Function(int, int?)? onPlay;
  final Function(int)? onMarkFinished;
  final Function(int)? onClearProgress;
  final double cardSize;
  final double titleSize;

  const _AudiobookSection({
    required this.title,
    required this.books,
    required this.serverUrl,
    this.authToken,
    this.onBookTap,
    this.onToggleFavorite,
    this.onPlay,
    this.onMarkFinished,
    this.onClearProgress,
    this.cardSize = 140,
    this.titleSize = 16,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Text(
            title,
            style: TextStyle(
              fontSize: titleSize,
              fontWeight: FontWeight.bold,
              color: sapphoText,
            ),
          ),
        ),
        SizedBox(
          height: cardSize + 60, // Card + text height
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: books.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              return AudiobookCard(
                book: books[index],
                serverUrl: serverUrl,
                authToken: authToken,
                size: cardSize,
                onTap: () => onBookTap?.call(books[index].id),
                onLongPress: () => _showContextMenu(context, books[index]),
              );
            },
          ),
        ),
        const SizedBox(height: 24), // Match Android spacing
      ],
    );
  }

  void _showContextMenu(BuildContext context, Audiobook book) {
    final hasProgress =
        (book.progress?.position ?? 0) > 0 || book.progress?.completed == 1;
    final isCompleted = book.progress?.completed == 1;

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
            // Header with book info
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                border: Border(bottom: BorderSide(color: sapphoSurfaceBorder)),
              ),
              child: Row(
                children: [
                  // Small cover
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: sapphoSurfaceLight,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: book.coverImage != null && serverUrl != null
                        ? CachedNetworkImage(
                            imageUrl:
                                '$serverUrl/api/audiobooks/${book.id}/cover',
                            fit: BoxFit.cover,
                            httpHeaders: authToken != null
                                ? {'Authorization': 'Bearer $authToken'}
                                : null,
                          )
                        : Center(
                            child: Text(
                              book.title.isNotEmpty ? book.title[0] : '?',
                              style: const TextStyle(
                                color: sapphoInfo,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          book.title,
                          style: const TextStyle(
                            color: sapphoText,
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (book.author != null)
                          Text(
                            book.author!,
                            style: const TextStyle(
                              color: sapphoTextMuted,
                              fontSize: 14,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Play option
            ListTile(
              leading: Icon(
                hasProgress && !isCompleted
                    ? Icons.play_arrow
                    : Icons.play_circle_fill,
                color: sapphoSuccess,
              ),
              title: Text(
                isCompleted
                    ? 'Listen Again'
                    : hasProgress
                    ? 'Continue Listening'
                    : 'Play',
                style: const TextStyle(color: sapphoText),
              ),
              onTap: () {
                Navigator.pop(context);
                onPlay?.call(book.id, book.progress?.position);
              },
            ),

            // View Details
            ListTile(
              leading: const Icon(Icons.info_outline, color: sapphoInfo),
              title: const Text(
                'View Details',
                style: TextStyle(color: sapphoText),
              ),
              onTap: () {
                Navigator.pop(context);
                onBookTap?.call(book.id);
              },
            ),

            // Add/Remove from Reading List
            ListTile(
              leading: Icon(
                book.isFavorite ? Icons.bookmark_remove : Icons.bookmark_add,
                color: sapphoInfo,
              ),
              title: Text(
                book.isFavorite
                    ? 'Remove from Reading List'
                    : 'Add to Reading List',
                style: const TextStyle(color: sapphoText),
              ),
              onTap: () {
                Navigator.pop(context);
                onToggleFavorite?.call(book.id);
              },
            ),

            // Add to Collection
            ListTile(
              leading: const Icon(Icons.folder_outlined, color: sapphoInfo),
              title: const Text(
                'Add to Collection',
                style: TextStyle(color: sapphoText),
              ),
              onTap: () {
                Navigator.pop(context);
                _showCollectionsSheet(context, book.id);
              },
            ),

            // Mark as Finished
            if (!isCompleted)
              ListTile(
                leading: const Icon(
                  Icons.check_circle_outline,
                  color: sapphoSuccess,
                ),
                title: const Text(
                  'Mark as Finished',
                  style: TextStyle(color: sapphoText),
                ),
                onTap: () {
                  Navigator.pop(context);
                  onMarkFinished?.call(book.id);
                },
              ),

            // Clear Progress
            if (hasProgress)
              ListTile(
                leading: const Icon(Icons.replay, color: sapphoWarning),
                title: const Text(
                  'Clear Progress',
                  style: TextStyle(color: sapphoText),
                ),
                onTap: () {
                  Navigator.pop(context);
                  onClearProgress?.call(book.id);
                },
              ),
          ],
        ),
      ),
    );
  }

  void _showCollectionsSheet(BuildContext context, int audiobookId) {
    // Show collections bottom sheet
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

/// Bottom sheet for managing book collections (copied from detail screen)
class _CollectionsBottomSheet extends StatefulWidget {
  final int audiobookId;

  const _CollectionsBottomSheet({required this.audiobookId});

  @override
  State<_CollectionsBottomSheet> createState() =>
      _CollectionsBottomSheetState();
}

class _CollectionsBottomSheetState extends State<_CollectionsBottomSheet> {
  List<dynamic> _collections = [];
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
      final api = Provider.of<ApiService>(context, listen: false);
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
      final api = Provider.of<ApiService>(context, listen: false);
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
                        children: const [
                          Icon(
                            Icons.folder_outlined,
                            size: 48,
                            color: sapphoIconDefault,
                          ),
                          SizedBox(height: 16),
                          Text(
                            'No collections yet',
                            style: TextStyle(color: sapphoText, fontSize: 16),
                          ),
                          SizedBox(height: 8),
                          Text(
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
                        return Container(
                          margin: const EdgeInsets.only(bottom: 8),
                          child: Material(
                            color: isMember
                                ? sapphoInfo.withValues(alpha: 0.1)
                                : sapphoSurfaceLight,
                            borderRadius: BorderRadius.circular(8),
                            child: InkWell(
                              onTap: _isUpdating
                                  ? null
                                  : () => _toggleCollection(collection.id),
                              borderRadius: BorderRadius.circular(8),
                              child: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(8),
                                  border: isMember
                                      ? Border.all(
                                          color: sapphoInfo.withValues(
                                            alpha: 0.3,
                                          ),
                                        )
                                      : null,
                                ),
                                child: Row(
                                  children: [
                                    Icon(
                                      isMember
                                          ? Icons.folder
                                          : Icons.folder_outlined,
                                      color: isMember
                                          ? sapphoInfo
                                          : sapphoIconDefault,
                                      size: 24,
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            collection.name,
                                            style: TextStyle(
                                              fontSize: 14,
                                              fontWeight: FontWeight.w500,
                                              color: isMember
                                                  ? sapphoInfo
                                                  : sapphoText,
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
                                        color: isMember
                                            ? sapphoInfo
                                            : Colors.transparent,
                                        shape: BoxShape.circle,
                                        border: Border.all(
                                          color: isMember
                                              ? sapphoInfo
                                              : sapphoIconDefault,
                                          width: 2,
                                        ),
                                      ),
                                      child: isMember
                                          ? const Icon(
                                              Icons.check,
                                              color: Colors.white,
                                              size: 16,
                                            )
                                          : null,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
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

/// Audiobook card matching Android's AudiobookCard composable
class AudiobookCard extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final double size;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  const AudiobookCard({
    super.key,
    required this.book,
    required this.serverUrl,
    this.authToken,
    this.size = 140,
    this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final textScale = size / 140;
    final titleFontSize = 14 * textScale;
    final authorFontSize = 12 * textScale;
    final placeholderFontSize = 32 * textScale;

    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: SizedBox(
        width: size,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Cover image
            Stack(
              children: [
                Container(
                  width: size,
                  height: size,
                  decoration: BoxDecoration(
                    color: sapphoSurfaceLight,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: _buildCoverImage(placeholderFontSize),
                ),

                // Progress bar at bottom
                if (book.progress != null && book.duration != null)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: _ProgressBar(
                      progress: book.progress!,
                      duration: book.duration!,
                    ),
                  ),

                // Reading list ribbon (top-right)
                if (book.isFavorite)
                  Positioned(
                    top: 0,
                    right: 8,
                    child: CustomPaint(
                      size: const Size(16, 28),
                      painter: _ReadingListRibbonPainter(),
                    ),
                  ),

                // Completed checkmark (top-left)
                if (book.progress?.completed == 1)
                  Positioned(
                    top: 4,
                    left: 4,
                    child: Container(
                      width: 24,
                      height: 24,
                      decoration: const BoxDecoration(
                        color: sapphoSuccess,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.check,
                        color: Colors.white,
                        size: 16,
                      ),
                    ),
                  ),
              ],
            ),

            const SizedBox(height: 8),

            // Title - use marquee if too long
            _MarqueeText(
              text: book.title,
              style: TextStyle(
                fontSize: titleFontSize,
                fontWeight: FontWeight.w500,
                color: sapphoText,
              ),
              maxLines: 1,
            ),

            // Author
            if (book.author != null)
              Text(
                book.author!,
                style: TextStyle(
                  fontSize: authorFontSize,
                  color: sapphoIconDefault,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildCoverImage(double placeholderFontSize) {
    if (book.coverImage != null && serverUrl != null) {
      final imageUrl = '$serverUrl/api/audiobooks/${book.id}/cover';
      return CachedNetworkImage(
        imageUrl: imageUrl,
        fit: BoxFit.cover,
        width: size,
        height: size,
        memCacheWidth: (size * 2).toInt(), // Cache at 2x for crisp display
        memCacheHeight: (size * 2).toInt(),
        fadeInDuration: Duration.zero, // Remove fade for faster appearance
        fadeOutDuration: Duration.zero,
        httpHeaders: authToken != null
            ? {'Authorization': 'Bearer $authToken'}
            : null,
        placeholder: (context, url) => _buildPlaceholder(placeholderFontSize),
        errorWidget: (context, url, error) =>
            _buildPlaceholder(placeholderFontSize),
      );
    }
    return _buildPlaceholder(placeholderFontSize);
  }

  Widget _buildPlaceholder(double fontSize) {
    return Center(
      child: Text(
        book.title.length >= 2 ? book.title.substring(0, 2) : book.title,
        style: TextStyle(
          fontSize: fontSize,
          fontWeight: FontWeight.bold,
          color: sapphoInfo,
        ),
      ),
    );
  }
}

/// Progress bar widget matching Android's progress bar
class _ProgressBar extends StatelessWidget {
  final Progress progress;
  final int duration;

  const _ProgressBar({required this.progress, required this.duration});

  @override
  Widget build(BuildContext context) {
    final progressPercent = progress.position / duration;
    final isCompleted = progress.completed == 1;

    return Container(
      height: 6,
      color: Colors.black.withValues(alpha: 0.5),
      child: FractionallySizedBox(
        widthFactor: isCompleted ? 1.0 : progressPercent.clamp(0.0, 1.0),
        alignment: Alignment.centerLeft,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: isCompleted
                  ? [sapphoSuccess, legacyGreenLight]
                  : [sapphoInfo, legacyBlueLight],
            ),
          ),
        ),
      ),
    );
  }
}

/// Reading list ribbon painter - vertical bookmark-style ribbon
class _ReadingListRibbonPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = sapphoInfo
      ..style = PaintingStyle.fill;

    // Bookmark ribbon shape - vertical with notch at bottom
    final path = Path()
      ..moveTo(0, 0)
      ..lineTo(size.width, 0)
      ..lineTo(size.width, size.height)
      ..lineTo(size.width / 2, size.height * 0.7)
      ..lineTo(0, size.height)
      ..close();

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Skeleton loading screen matching Android's SkeletonHomeScreen
class _SkeletonHomeScreen extends StatefulWidget {
  const _SkeletonHomeScreen();

  @override
  State<_SkeletonHomeScreen> createState() => _SkeletonHomeScreenState();
}

class _SkeletonHomeScreenState extends State<_SkeletonHomeScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat();
    _animation = Tween<double>(begin: 0, end: 1).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return ListView(
          padding: const EdgeInsets.symmetric(vertical: 16),
          children: [
            // Continue Listening skeleton (large cards)
            _SkeletonSection(
              titleWidth: 160,
              cardCount: 3,
              cardSize: 180,
              animation: _animation,
            ),

            // Up Next skeleton
            _SkeletonSection(
              titleWidth: 80,
              cardCount: 4,
              cardSize: 140,
              animation: _animation,
            ),

            // Recently Added skeleton
            _SkeletonSection(
              titleWidth: 120,
              cardCount: 4,
              cardSize: 140,
              animation: _animation,
            ),
          ],
        );
      },
    );
  }
}

class _SkeletonSection extends StatelessWidget {
  final double titleWidth;
  final int cardCount;
  final double cardSize;
  final Animation<double> animation;

  const _SkeletonSection({
    required this.titleWidth,
    required this.cardCount,
    required this.cardSize,
    required this.animation,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Title skeleton
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: _ShimmerBox(
            width: titleWidth,
            height: 20,
            animation: animation,
          ),
        ),
        SizedBox(
          height: cardSize + 40,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: cardCount,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              return _SkeletonCard(size: cardSize, animation: animation);
            },
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}

class _SkeletonCard extends StatelessWidget {
  final double size;
  final Animation<double> animation;

  const _SkeletonCard({required this.size, required this.animation});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Cover placeholder
          _ShimmerBox(
            width: size,
            height: size,
            borderRadius: 12,
            animation: animation,
          ),
          const SizedBox(height: 8),
          // Title placeholder
          _ShimmerBox(width: size, height: 14, animation: animation),
          const SizedBox(height: 4),
          // Author placeholder
          _ShimmerBox(width: size * 0.7, height: 12, animation: animation),
        ],
      ),
    );
  }
}

class _ShimmerBox extends StatelessWidget {
  final double width;
  final double height;
  final double borderRadius;
  final Animation<double> animation;

  const _ShimmerBox({
    required this.width,
    required this.height,
    this.borderRadius = 4,
    required this.animation,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        gradient: LinearGradient(
          begin: Alignment(-1 + 2 * animation.value, 0),
          end: Alignment(1 + 2 * animation.value, 0),
          colors: const [
            Color(0x99252525),
            Color(0x33252525),
            Color(0x99252525),
          ],
        ),
      ),
    );
  }
}

/// Text widget that scrolls horizontally if text is too long
class _MarqueeText extends StatelessWidget {
  final String text;
  final TextStyle style;
  final int maxLines;

  const _MarqueeText({
    required this.text,
    required this.style,
    this.maxLines = 1,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // Measure text width
        final textPainter = TextPainter(
          text: TextSpan(text: text, style: style),
          maxLines: maxLines,
          textDirection: TextDirection.ltr,
        )..layout(maxWidth: double.infinity);

        final textWidth = textPainter.width;
        final availableWidth = constraints.maxWidth;

        // If text fits, just show it normally
        if (textWidth <= availableWidth) {
          return Text(
            text,
            style: style,
            maxLines: maxLines,
            overflow: TextOverflow.ellipsis,
          );
        }

        // Text is too long, use marquee
        return SizedBox(
          height: style.fontSize! * 1.3,
          child: Marquee(
            text: text,
            style: style,
            scrollAxis: Axis.horizontal,
            blankSpace: 40.0,
            velocity: 30.0,
            pauseAfterRound: const Duration(seconds: 2),
            startPadding: 0.0,
            accelerationDuration: const Duration(milliseconds: 500),
            accelerationCurve: Curves.easeIn,
            decelerationDuration: const Duration(milliseconds: 500),
            decelerationCurve: Curves.easeOut,
          ),
        );
      },
    );
  }
}
