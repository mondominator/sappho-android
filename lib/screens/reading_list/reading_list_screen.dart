import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/audiobook.dart';
import '../../providers/library_provider.dart';
import '../../theme/app_theme.dart';

/// Reading list screen (favorites) matching Android ReadingListScreen.kt
class ReadingListScreen extends StatelessWidget {
  final Function(int)? onAudiobookTap;
  final VoidCallback? onBack;

  const ReadingListScreen({super.key, this.onAudiobookTap, this.onBack});

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: Consumer<LibraryProvider>(
        builder: (context, library, child) {
          final favorites = library.readingList;

          return Column(
            children: [
              // Header
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 16,
                ),
                child: Row(
                  children: [
                    IconButton(
                      onPressed: onBack,
                      icon: const Icon(Icons.arrow_back, color: Colors.white),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Reading List',
                            style: TextStyle(
                              fontSize: 28,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                          ),
                          Text(
                            '${favorites.length} books to read',
                            style: const TextStyle(
                              fontSize: 14,
                              color: sapphoIconDefault,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              // Content
              Expanded(
                child: library.isLoading
                    ? const Center(
                        child: CircularProgressIndicator(color: sapphoInfo),
                      )
                    : favorites.isEmpty
                    ? _EmptyState()
                    : _FavoritesGrid(
                        favorites: favorites,
                        serverUrl: library.serverUrl,
                        authToken: library.authToken,
                        onAudiobookTap: onAudiobookTap,
                        bottomPadding: bottomPadding,
                      ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.bookmark_added, size: 48, color: sapphoInfo),
          const SizedBox(height: 16),
          const Text(
            'Your reading list is empty',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Add books to your reading list from the book detail page',
            style: TextStyle(fontSize: 14, color: sapphoIconDefault),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _FavoritesGrid extends StatelessWidget {
  final List<Audiobook> favorites;
  final String? serverUrl;
  final String? authToken;
  final Function(int)? onAudiobookTap;
  final double bottomPadding;

  const _FavoritesGrid({
    required this.favorites,
    this.serverUrl,
    this.authToken,
    this.onAudiobookTap,
    required this.bottomPadding,
  });

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 0.72,
      ),
      itemCount: favorites.length,
      itemBuilder: (context, index) {
        final book = favorites[index];
        return _BookItem(
          book: book,
          serverUrl: serverUrl,
          authToken: authToken,
          onTap: () => onAudiobookTap?.call(book.id),
        );
      },
    );
  }
}

class _BookItem extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback? onTap;

  const _BookItem({
    required this.book,
    this.serverUrl,
    this.authToken,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final progress = book.progress;
    final isCompleted = progress?.completed == 1;
    final progressPercent = isCompleted
        ? 1.0
        : (progress != null && book.duration != null && book.duration! > 0)
        ? (progress.position / book.duration!).clamp(0.0, 1.0)
        : 0.0;
    // Use userRating if available, fall back to rating or averageRating
    final displayRating = book.userRating ?? book.rating ?? book.averageRating;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: sapphoSurfaceLight,
          borderRadius: BorderRadius.circular(8),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Cover with progress bar
            Expanded(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  // Cover image
                  if (book.coverImage != null && serverUrl != null)
                    CachedNetworkImage(
                      imageUrl: '$serverUrl/api/audiobooks/${book.id}/cover',
                      fit: BoxFit.cover,
                      memCacheWidth: 300,
                      memCacheHeight: 300,
                      fadeInDuration: Duration.zero,
                      fadeOutDuration: Duration.zero,
                      httpHeaders: authToken != null
                          ? {'Authorization': 'Bearer $authToken'}
                          : null,
                      placeholder: (_, __) => _buildPlaceholder(),
                      errorWidget: (_, __, ___) => _buildPlaceholder(),
                    )
                  else
                    _buildPlaceholder(),

                  // Progress bar at bottom
                  if (progressPercent > 0)
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: Container(
                        height: 4,
                        color: Colors.black.withValues(alpha: 0.7),
                        child: FractionallySizedBox(
                          alignment: Alignment.centerLeft,
                          widthFactor: progressPercent,
                          child: Container(
                            decoration: BoxDecoration(
                              gradient: LinearGradient(
                                colors: isCompleted
                                    ? [sapphoSuccess, const Color(0xFF22C55E)]
                                    : [sapphoInfo, const Color(0xFF60A5FA)],
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),

            // Book info
            Padding(
              padding: const EdgeInsets.all(6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Title - marquee for long titles
                  SizedBox(
                    height: 14,
                    width: double.infinity,
                    child: _MarqueeText(
                      text: book.title,
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 2),
                  // Duration and rating row
                  Row(
                    children: [
                      // Duration
                      if (book.duration != null)
                        Text(
                          '${book.duration! ~/ 3600}h ${(book.duration! % 3600) ~/ 60}m',
                          style: const TextStyle(
                            fontSize: 9,
                            color: sapphoIconDefault,
                          ),
                        ),
                      // Rating
                      if (displayRating != null && displayRating > 0) ...[
                        if (book.duration != null)
                          const Text(
                            ' • ',
                            style: TextStyle(
                              fontSize: 9,
                              color: sapphoIconDefault,
                            ),
                          ),
                        const Icon(
                          Icons.star,
                          size: 9,
                          color: sapphoStarFilled,
                        ),
                        const SizedBox(width: 1),
                        Text(
                          displayRating.toStringAsFixed(1),
                          style: const TextStyle(
                            fontSize: 9,
                            color: sapphoIconDefault,
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [sapphoProgressTrack, sapphoSurfaceDark],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Center(
        child: Text(
          book.title.length >= 2
              ? book.title.substring(0, 2).toUpperCase()
              : book.title.toUpperCase(),
          style: const TextStyle(
            fontSize: 32,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
      ),
    );
  }
}

/// Blue ribbon painter for marking reading list items (use elsewhere in app)
class RibbonPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = sapphoInfo
      ..style = PaintingStyle.fill;

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

/// Marquee text widget for long titles
class _MarqueeText extends StatefulWidget {
  final String text;
  final TextStyle style;

  const _MarqueeText({required this.text, required this.style});

  @override
  State<_MarqueeText> createState() => _MarqueeTextState();
}

class _MarqueeTextState extends State<_MarqueeText> {
  late ScrollController _scrollController;
  bool _isOverflowing = false;
  bool _scrollingForward = true;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Future.delayed(const Duration(milliseconds: 100), _checkOverflow);
    });
  }

  @override
  void didUpdateWidget(_MarqueeText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text) {
      _stopScrolling();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        Future.delayed(const Duration(milliseconds: 100), _checkOverflow);
      });
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _checkOverflow() {
    if (!mounted || !_scrollController.hasClients) return;
    final maxScroll = _scrollController.position.maxScrollExtent;
    if (maxScroll > 0 && !_isOverflowing) {
      setState(() => _isOverflowing = true);
      _startScrolling();
    }
  }

  void _stopScrolling() {
    _isOverflowing = false;
    _scrollingForward = true;
  }

  void _startScrolling() async {
    if (!mounted || !_isOverflowing) return;
    await Future.delayed(const Duration(seconds: 2));
    _scroll();
  }

  void _scroll() async {
    if (!mounted || !_isOverflowing || !_scrollController.hasClients) return;

    final maxScroll = _scrollController.position.maxScrollExtent;
    final targetPosition = _scrollingForward ? maxScroll : 0.0;
    final duration = Duration(
      milliseconds: (maxScroll * 30).toInt().clamp(1000, 5000),
    );

    await _scrollController.animateTo(
      targetPosition,
      duration: duration,
      curve: Curves.linear,
    );

    if (!mounted || !_isOverflowing) return;
    _scrollingForward = !_scrollingForward;
    await Future.delayed(const Duration(seconds: 2));
    _scroll();
  }

  @override
  Widget build(BuildContext context) {
    // Center text if not overflowing, otherwise use scroll view for marquee
    if (!_isOverflowing) {
      return Text(
        widget.text,
        style: widget.style,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
    }

    return ClipRect(
      child: SingleChildScrollView(
        controller: _scrollController,
        scrollDirection: Axis.horizontal,
        physics: const NeverScrollableScrollPhysics(),
        child: Text(widget.text, style: widget.style, maxLines: 1),
      ),
    );
  }
}
