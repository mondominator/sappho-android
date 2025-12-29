import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../providers/player_provider.dart';
import '../../theme/app_theme.dart';

/// Minimized player bar matching Android MinimizedPlayerBar.kt
class MinimizedPlayerBar extends StatelessWidget {
  final VoidCallback? onExpand;

  const MinimizedPlayerBar({super.key, this.onExpand});

  @override
  Widget build(BuildContext context) {
    return Consumer<PlayerProvider>(
      builder: (context, player, child) {
        if (!player.hasAudiobook) {
          return const SizedBox.shrink();
        }

        final book = player.currentAudiobook!;

        return GestureDetector(
          onTap: onExpand,
          child: Container(
            height: 72,
            decoration: BoxDecoration(
              color: sapphoSurfaceLight,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.3),
                  blurRadius: 8,
                  offset: const Offset(0, -2),
                ),
              ],
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                children: [
                  // Cover art
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      color: sapphoProgressTrack,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: book.coverImage != null && player.serverUrl != null
                        ? CachedNetworkImage(
                            imageUrl:
                                '${player.serverUrl}/api/audiobooks/${book.id}/cover',
                            fit: BoxFit.cover,
                            memCacheWidth: 112,
                            memCacheHeight: 112,
                            fadeInDuration: Duration.zero,
                            fadeOutDuration: Duration.zero,
                            httpHeaders: player.authToken != null
                                ? {
                                    'Authorization':
                                        'Bearer ${player.authToken}',
                                  }
                                : null,
                            placeholder: (_, __) => _buildPlaceholder(book),
                            errorWidget: (_, __, ___) =>
                                _buildPlaceholder(book),
                          )
                        : _buildPlaceholder(book),
                  ),

                  const SizedBox(width: 12),

                  // Title and metadata
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _MarqueeText(
                          text: book.title,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                            color: Colors.white,
                            decoration: TextDecoration.none,
                          ),
                        ),
                        if (book.author != null)
                          Text(
                            book.author!,
                            style: const TextStyle(
                              fontSize: 12,
                              color: sapphoIconDefault,
                              decoration: TextDecoration.none,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        _PulsingTimeText(
                          currentPosition: player.currentPosition,
                          duration: player.duration,
                          isPlaying: player.isPlaying,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(width: 4),

                  // Seek back button
                  IconButton(
                    onPressed: () => player.skipBackward(),
                    icon: const Icon(
                      Icons.replay_10,
                      color: sapphoIconDefault,
                      size: 32,
                    ),
                  ),

                  // Play/Pause button with animation
                  _AnimatedPlayButton(player: player),

                  // Seek forward button
                  IconButton(
                    onPressed: () => player.skipForward(),
                    icon: const Icon(
                      Icons.forward_10,
                      color: sapphoIconDefault,
                      size: 32,
                    ),
                  ),

                  const SizedBox(width: 8),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildPlaceholder(dynamic book) {
    return Center(
      child: Text(
        book.title.isNotEmpty ? book.title[0].toUpperCase() : 'A',
        style: const TextStyle(
          color: sapphoInfo,
          fontSize: 24,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

/// Marquee text that scrolls if too long
class _MarqueeText extends StatefulWidget {
  final String text;
  final TextStyle style;

  const _MarqueeText({required this.text, required this.style});

  @override
  State<_MarqueeText> createState() => _MarqueeTextState();
}

class _MarqueeTextState extends State<_MarqueeText> {
  final ScrollController _scrollController = ScrollController();
  Timer? _scrollTimer;

  @override
  void initState() {
    super.initState();
    _startScrolling();
  }

  @override
  void dispose() {
    _scrollTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _startScrolling() {
    _scrollTimer = Timer.periodic(const Duration(milliseconds: 500), (
      timer,
    ) async {
      if (!mounted || !_scrollController.hasClients) return;

      final maxScroll = _scrollController.position.maxScrollExtent;
      if (maxScroll <= 0) return;

      final currentPosition = _scrollController.offset;

      if (currentPosition >= maxScroll) {
        // Scroll back to start
        await _scrollController.animateTo(
          0,
          duration: Duration(
            milliseconds: (maxScroll * 20).toInt().clamp(2000, 10000),
          ),
          curve: Curves.linear,
        );
        await Future.delayed(const Duration(milliseconds: 1500));
      } else if (currentPosition == 0) {
        await Future.delayed(const Duration(milliseconds: 1500));
        if (!mounted) return;
        // Scroll to end
        await _scrollController.animateTo(
          maxScroll,
          duration: Duration(
            milliseconds: (maxScroll * 20).toInt().clamp(2000, 10000),
          ),
          curve: Curves.linear,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      controller: _scrollController,
      scrollDirection: Axis.horizontal,
      physics: const NeverScrollableScrollPhysics(),
      child: Text(
        widget.text,
        style: widget.style,
        maxLines: 1,
        softWrap: false,
      ),
    );
  }
}

/// Time text that pulses when playing
class _PulsingTimeText extends StatefulWidget {
  final int currentPosition;
  final int duration;
  final bool isPlaying;

  const _PulsingTimeText({
    required this.currentPosition,
    required this.duration,
    required this.isPlaying,
  });

  @override
  State<_PulsingTimeText> createState() => _PulsingTimeTextState();
}

class _PulsingTimeTextState extends State<_PulsingTimeText>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(seconds: 2),
      vsync: this,
    )..repeat(reverse: true);

    _colorAnimation = ColorTween(
      begin: legacyBlueLight,
      end: legacyBluePale,
    ).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final timeText =
        '${_formatTime(widget.currentPosition)} / ${_formatTime(widget.duration)}';

    if (widget.isPlaying) {
      return AnimatedBuilder(
        animation: _colorAnimation,
        builder: (context, child) {
          return Text(
            timeText,
            style: TextStyle(
              fontSize: 11,
              color: _colorAnimation.value,
              decoration: TextDecoration.none,
            ),
          );
        },
      );
    }

    return Text(
      timeText,
      style: const TextStyle(
        fontSize: 11,
        color: sapphoTextMuted,
        decoration: TextDecoration.none,
      ),
    );
  }

  String _formatTime(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    final secs = seconds % 60;
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
    }
    return '$minutes:${secs.toString().padLeft(2, '0')}';
  }
}

/// Animated play/pause button with pulsing effect when playing
class _AnimatedPlayButton extends StatefulWidget {
  final PlayerProvider player;

  const _AnimatedPlayButton({required this.player});

  @override
  State<_AnimatedPlayButton> createState() => _AnimatedPlayButtonState();
}

class _AnimatedPlayButtonState extends State<_AnimatedPlayButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _scaleAnimation;
  late Animation<double> _alphaAnimation;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      duration: const Duration(seconds: 3),
      vsync: this,
    )..repeat(reverse: true);

    _scaleAnimation = Tween<double>(begin: 1.0, end: 1.12).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.fastOutSlowIn),
    );

    _alphaAnimation = Tween<double>(begin: 0.6, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.fastOutSlowIn),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isPlaying = widget.player.isPlaying;

    return AnimatedBuilder(
      animation: _pulseController,
      builder: (context, child) {
        return Transform.scale(
          scale: isPlaying ? _scaleAnimation.value : 1.0,
          child: Opacity(
            opacity: isPlaying ? _alphaAnimation.value : 1.0,
            child: GestureDetector(
              onTap: () => widget.player.togglePlayPause(),
              child: Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: isPlaying ? sapphoSuccess : sapphoInfo,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: (isPlaying ? sapphoSuccess : sapphoInfo)
                          .withValues(alpha: isPlaying ? 0.4 : 0.2),
                      blurRadius: isPlaying ? 20 : 8,
                      spreadRadius: isPlaying ? 2 : 0,
                    ),
                  ],
                ),
                child: Icon(
                  isPlaying ? Icons.pause : Icons.play_arrow,
                  color: Colors.white,
                  size: 36,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
