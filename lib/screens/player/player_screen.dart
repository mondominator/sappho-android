import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:cast_plus/cast.dart';
import '../../models/audiobook.dart';
import '../../providers/player_provider.dart';
import '../../services/cast_service.dart';
import '../../theme/app_theme.dart';

/// Full player screen matching Android PlayerActivity.kt
class PlayerScreen extends StatefulWidget {
  final int audiobookId;
  final int? startPosition;
  final bool fromMinimized;
  final VoidCallback? onMinimize;

  const PlayerScreen({
    super.key,
    required this.audiobookId,
    this.startPosition,
    this.fromMinimized = false,
    this.onMinimize,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen>
    with SingleTickerProviderStateMixin {
  double _dragOffset = 0;
  bool _isMinimizing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final player = context.read<PlayerProvider>();
      if (!widget.fromMinimized) {
        player.loadAndPlay(
          widget.audiobookId,
          startPosition: widget.startPosition ?? 0,
        );
      } else {
        player.loadAudiobookDetails(widget.audiobookId);
      }
    });
  }

  void _handleMinimize() {
    setState(() => _isMinimizing = true);
    Future.delayed(const Duration(milliseconds: 300), () {
      widget.onMinimize?.call();
    });
  }

  void _showCastDialog(BuildContext context) {
    final player = context.read<PlayerProvider>();
    showDialog(
      context: context,
      builder: (dialogContext) =>
          _CastDialog(audiobookId: widget.audiobookId, player: player),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;
    final topPadding = MediaQuery.of(context).padding.top;

    return Consumer<PlayerProvider>(
      builder: (context, player, child) {
        final book = player.currentAudiobook;

        return GestureDetector(
          onVerticalDragUpdate: (details) {
            if (details.delta.dy > 0) {
              setState(() => _dragOffset += details.delta.dy);
            } else if (_dragOffset > 0) {
              setState(
                () => _dragOffset = (_dragOffset + details.delta.dy).clamp(
                  0,
                  double.infinity,
                ),
              );
            }
          },
          onVerticalDragEnd: (details) {
            if (_dragOffset > 150) {
              _handleMinimize();
            } else {
              setState(() => _dragOffset = 0);
            }
          },
          child: AnimatedContainer(
            duration: Duration(milliseconds: _isMinimizing ? 300 : 0),
            transform: Matrix4.translationValues(
              0,
              _isMinimizing ? 2000 : _dragOffset,
              0,
            ),
            child: Scaffold(
              backgroundColor: sapphoBackground,
              body: Column(
                children: [
                  SizedBox(height: topPadding),

                  // Top bar with minimize and cast
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        IconButton(
                          onPressed: _handleMinimize,
                          icon: const Icon(
                            Icons.keyboard_arrow_down,
                            color: Colors.white,
                            size: 28,
                          ),
                        ),
                        ListenableBuilder(
                          listenable: CastService(),
                          builder: (context, _) {
                            final castService = CastService();
                            return IconButton(
                              onPressed: () => _showCastDialog(context),
                              icon: Icon(
                                castService.isCasting
                                    ? Icons.cast_connected
                                    : Icons.cast,
                                color: castService.isCasting
                                    ? sapphoInfo
                                    : sapphoIconDefault,
                                size: 24,
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),

                  if (player.isLoading || book == null)
                    Expanded(
                      child: Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const CircularProgressIndicator(color: sapphoInfo),
                            if (player.error != null) ...[
                              const SizedBox(height: 16),
                              Text(
                                'Error: ${player.error}',
                                style: const TextStyle(color: sapphoError),
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ],
                        ),
                      ),
                    )
                  else
                    Expanded(
                      child: _PlayerContent(
                        player: player,
                        book: book,
                        onShowChapters: () {
                          // TODO: Implement chapters dialog
                        },
                        onShowSpeed: () {
                          // TODO: Implement playback speed dialog
                        },
                        onShowSleepTimer: () {
                          // TODO: Implement sleep timer dialog
                        },
                      ),
                    ),

                  SizedBox(height: bottomPadding),
                ],
              ),
            ),
          ),
        );

        // Dialogs
      },
    );
  }
}

class _PlayerContent extends StatelessWidget {
  final PlayerProvider player;
  final Audiobook book;
  final VoidCallback onShowChapters;
  final VoidCallback onShowSpeed;
  final VoidCallback onShowSleepTimer;

  const _PlayerContent({
    required this.player,
    required this.book,
    required this.onShowChapters,
    required this.onShowSpeed,
    required this.onShowSleepTimer,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        children: [
          const SizedBox(height: 16),

          // Cover art
          _CoverArt(
            book: book,
            serverUrl: player.serverUrl,
            authToken: player.authToken,
          ),

          const SizedBox(height: 24),

          // Title
          Text(
            book.title,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),

          const SizedBox(height: 4),

          // Author
          if (book.author != null)
            Text(
              book.author!,
              style: const TextStyle(fontSize: 16, color: legacyBlueLight),
              textAlign: TextAlign.center,
            ),

          // Series
          if (book.series != null) ...[
            const SizedBox(height: 2),
            Text(
              '${book.series}${book.seriesPosition != null ? ' #${book.formattedSeriesPosition}' : ''}',
              style: TextStyle(
                fontSize: 14,
                color: legacyBlueLight.withValues(alpha: 0.7),
              ),
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],

          const SizedBox(height: 24),

          // Playback controls
          _PlaybackControls(player: player),

          const SizedBox(height: 24),

          // Progress slider
          _ProgressSlider(player: player),

          const SizedBox(height: 16),

          // Control buttons row (Chapters, Speed, Sleep Timer)
          _ControlButtonsRow(
            player: player,
            onShowChapters: onShowChapters,
            onShowSpeed: onShowSpeed,
            onShowSleepTimer: onShowSleepTimer,
          ),

          // Playing animation - takes remaining space
          Flexible(
            child: player.isPlaying
                ? Center(child: _PlayingAnimation())
                : const SizedBox.shrink(),
          ),
        ],
      ),
    );
  }
}

class _CoverArt extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;

  const _CoverArt({required this.book, this.serverUrl, this.authToken});

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    // Use smaller cover on shorter screens (iOS with safe areas)
    final coverSize = screenHeight < 700
        ? screenWidth * 0.5
        : screenWidth * 0.6;

    return Container(
      width: coverSize,
      height: coverSize,
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.4),
            blurRadius: 20,
            offset: const Offset(0, 10),
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
          fontSize: 48,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class _PlaybackControls extends StatelessWidget {
  final PlayerProvider player;

  const _PlaybackControls({required this.player});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        // Previous chapter
        IconButton(
          onPressed: player.chapters.isNotEmpty
              ? () => player.previousChapter()
              : null,
          icon: Icon(
            Icons.skip_previous,
            color: player.chapters.isEmpty ? sapphoTextMuted : Colors.white,
            size: 32,
          ),
        ),

        // Skip backward 10s
        IconButton(
          onPressed: () => player.skipBackward(),
          icon: const Icon(Icons.replay_10, color: Colors.white, size: 36),
        ),

        // Play/Pause button
        GestureDetector(
          onTap: () => player.togglePlayPause(),
          child: Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(
              color: sapphoInfo,
              shape: BoxShape.circle,
            ),
            child: player.isLoading
                ? const Padding(
                    padding: EdgeInsets.all(20),
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 3,
                    ),
                  )
                : Icon(
                    player.isPlaying ? Icons.pause : Icons.play_arrow,
                    color: Colors.white,
                    size: 36,
                  ),
          ),
        ),

        // Skip forward 10s
        IconButton(
          onPressed: () => player.skipForward(),
          icon: const Icon(Icons.forward_10, color: Colors.white, size: 36),
        ),

        // Next chapter
        IconButton(
          onPressed: player.chapters.isNotEmpty
              ? () => player.nextChapter()
              : null,
          icon: Icon(
            Icons.skip_next,
            color: player.chapters.isEmpty ? sapphoTextMuted : Colors.white,
            size: 32,
          ),
        ),
      ],
    );
  }
}

class _ProgressSlider extends StatefulWidget {
  final PlayerProvider player;

  const _ProgressSlider({required this.player});

  @override
  State<_ProgressSlider> createState() => _ProgressSliderState();
}

class _ProgressSliderState extends State<_ProgressSlider> {
  bool _isDragging = false;
  double _dragPosition = 0;
  bool _wasPlayingBeforeDrag = false;

  @override
  Widget build(BuildContext context) {
    final player = widget.player;
    final displayedPosition = _isDragging
        ? _dragPosition
        : player.currentPosition.toDouble();

    return Column(
      children: [
        // Time popup while dragging
        SizedBox(
          height: 40,
          child: _isDragging
              ? Center(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: sapphoSurfaceLight,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _formatTime(_dragPosition.toInt()),
                      style: const TextStyle(
                        color: sapphoInfo,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                )
              : null,
        ),

        // Time labels
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              _formatTime(displayedPosition.toInt()),
              style: TextStyle(
                color: _isDragging ? sapphoInfo : sapphoIconDefault,
                fontSize: 12,
              ),
            ),
            Text(
              _formatTime(player.duration),
              style: const TextStyle(color: sapphoIconDefault, fontSize: 12),
            ),
          ],
        ),

        // Slider
        SliderTheme(
          data: SliderThemeData(
            trackHeight: 4,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
            activeTrackColor: sapphoInfo,
            inactiveTrackColor: sapphoProgressTrack,
            thumbColor: sapphoInfo,
            overlayColor: sapphoInfo.withValues(alpha: 0.2),
          ),
          child: Slider(
            value: displayedPosition.clamp(0, player.duration.toDouble()),
            max: player.duration.toDouble().clamp(1, double.infinity),
            onChangeStart: (value) {
              _wasPlayingBeforeDrag = player.isPlaying;
              setState(() {
                _isDragging = true;
                _dragPosition = value;
              });
            },
            onChanged: (value) {
              setState(() => _dragPosition = value);
            },
            onChangeEnd: (value) {
              if (_wasPlayingBeforeDrag) {
                player.seekToAndPlay(value.toInt());
              } else {
                player.seekTo(value.toInt());
              }
              setState(() => _isDragging = false);
            },
          ),
        ),
      ],
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

class _ControlButtonsRow extends StatelessWidget {
  final PlayerProvider player;
  final VoidCallback onShowChapters;
  final VoidCallback onShowSpeed;
  final VoidCallback onShowSleepTimer;

  const _ControlButtonsRow({
    required this.player,
    required this.onShowChapters,
    required this.onShowSpeed,
    required this.onShowSleepTimer,
  });

  @override
  Widget build(BuildContext context) {
    final hasSleepTimer =
        player.sleepTimerRemaining != null && player.sleepTimerRemaining! > 0;

    return Row(
      children: [
        // Chapters button
        Expanded(
          child: _ControlButton(
            icon: Icons.list,
            iconColor: legacyBlueLight,
            label: player.currentChapter?.title ?? '—',
            onTap: () => _showChaptersDialog(context),
            marquee: true,
          ),
        ),

        // Speed button
        Expanded(
          child: _ControlButton(
            icon: Icons.speed,
            iconColor: legacyPurpleLight,
            label: '${player.playbackSpeed}x',
            onTap: () => _showSpeedDialog(context),
          ),
        ),

        // Sleep timer button
        Expanded(
          child: _ControlButton(
            icon: Icons.bedtime,
            iconColor: hasSleepTimer ? sapphoStarFilled : sapphoWarning,
            label: hasSleepTimer
                ? _formatSleepTime(player.sleepTimerRemaining!)
                : 'Off',
            labelColor: hasSleepTimer ? sapphoStarFilled : null,
            onTap: () => _showSleepTimerDialog(context),
          ),
        ),
      ],
    );
  }

  void _showChaptersDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => _ChaptersDialog(player: player),
    );
  }

  void _showSpeedDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => _SpeedDialog(player: player),
    );
  }

  void _showSleepTimerDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => _SleepTimerDialog(player: player),
    );
  }

  String _formatSleepTime(int seconds) {
    final mins = seconds ~/ 60;
    final secs = seconds % 60;
    return '$mins:${secs.toString().padLeft(2, '0')}';
  }
}

class _ControlButton extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String label;
  final Color? labelColor;
  final VoidCallback onTap;
  final bool marquee;

  const _ControlButton({
    required this.icon,
    required this.iconColor,
    required this.label,
    this.labelColor,
    required this.onTap,
    this.marquee = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 4),
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: sapphoSurfaceLight,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            Icon(icon, size: 22, color: iconColor),
            const SizedBox(height: 4),
            if (marquee)
              SizedBox(
                height: 16,
                width: double.infinity,
                child: _MarqueeText(
                  text: label,
                  style: TextStyle(
                    fontSize: 11,
                    color: labelColor ?? Colors.white,
                  ),
                ),
              )
            else
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  color: labelColor ?? Colors.white,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
          ],
        ),
      ),
    );
  }
}

class _PlayingAnimation extends StatefulWidget {
  @override
  State<_PlayingAnimation> createState() => _PlayingAnimationState();
}

class _PlayingAnimationState extends State<_PlayingAnimation>
    with TickerProviderStateMixin {
  late List<AnimationController> _controllers;
  late List<Animation<double>> _animations;

  @override
  void initState() {
    super.initState();
    _controllers = List.generate(3, (index) {
      return AnimationController(
        duration: Duration(milliseconds: 400 + (index * 100)),
        vsync: this,
      )..repeat(reverse: true);
    });

    _animations = _controllers.map((controller) {
      return Tween<double>(begin: 0.3, end: 1.0).animate(controller);
    }).toList();
  }

  @override
  void dispose() {
    for (var controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: List.generate(3, (index) {
        return AnimatedBuilder(
          animation: _animations[index],
          builder: (context, child) {
            return Container(
              width: 3,
              height: 12 * _animations[index].value,
              margin: const EdgeInsets.symmetric(horizontal: 1.5),
              decoration: BoxDecoration(
                color: sapphoInfo,
                borderRadius: BorderRadius.circular(2),
              ),
            );
          },
        );
      }),
    );
  }
}

class _ChaptersDialog extends StatefulWidget {
  final PlayerProvider player;

  const _ChaptersDialog({required this.player});

  @override
  State<_ChaptersDialog> createState() => _ChaptersDialogState();
}

class _ChaptersDialogState extends State<_ChaptersDialog> {
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    // Scroll to current chapter after the dialog builds
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToCurrentChapter();
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToCurrentChapter() {
    final currentChapter = widget.player.currentChapter;
    if (currentChapter == null) return;

    final index = widget.player.chapters.indexOf(currentChapter);
    if (index >= 0) {
      // Each ListTile is approximately 56 pixels high
      const itemHeight = 56.0;
      final targetOffset =
          (index * itemHeight) - 112; // Center in view (2 items above)
      _scrollController.animateTo(
        targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentChapter = widget.player.currentChapter;

    return AlertDialog(
      backgroundColor: sapphoSurfaceLight,
      title: const Text('Chapters', style: TextStyle(color: Colors.white)),
      content: SizedBox(
        width: double.maxFinite,
        height: 400,
        child: ListView.builder(
          controller: _scrollController,
          itemCount: widget.player.chapters.length,
          itemBuilder: (context, index) {
            final chapter = widget.player.chapters[index];
            final isCurrentChapter = chapter == currentChapter;

            return ListTile(
              title: Text(
                chapter.title ?? 'Chapter ${index + 1}',
                style: TextStyle(
                  color: isCurrentChapter ? sapphoInfo : Colors.white,
                  fontWeight: isCurrentChapter
                      ? FontWeight.bold
                      : FontWeight.normal,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: Text(
                _formatTime(chapter.startTime.toInt()),
                style: const TextStyle(color: sapphoIconDefault, fontSize: 12),
              ),
              onTap: () {
                widget.player.jumpToChapter(chapter);
                Navigator.pop(context);
              },
            );
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close', style: TextStyle(color: sapphoInfo)),
        ),
      ],
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

class _SpeedDialog extends StatelessWidget {
  final PlayerProvider player;

  const _SpeedDialog({required this.player});

  @override
  Widget build(BuildContext context) {
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0];

    return AlertDialog(
      backgroundColor: sapphoSurfaceLight,
      title: const Text(
        'Playback Speed',
        style: TextStyle(color: Colors.white),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: speeds.map((speed) {
          return ListTile(
            title: Text(
              '${speed}x',
              style: TextStyle(
                color: speed == player.playbackSpeed
                    ? sapphoInfo
                    : Colors.white,
              ),
            ),
            onTap: () {
              player.setPlaybackSpeed(speed);
              Navigator.pop(context);
            },
          );
        }).toList(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close', style: TextStyle(color: sapphoInfo)),
        ),
      ],
    );
  }
}

class _SleepTimerDialog extends StatelessWidget {
  final PlayerProvider player;

  const _SleepTimerDialog({required this.player});

  @override
  Widget build(BuildContext context) {
    final timerOptions = [
      (0, 'Off'),
      (5, '5 minutes'),
      (10, '10 minutes'),
      (15, '15 minutes'),
      (30, '30 minutes'),
      (45, '45 minutes'),
      (60, '1 hour'),
      (90, '1.5 hours'),
      (120, '2 hours'),
    ];

    final hasActiveTimer =
        player.sleepTimerRemaining != null && player.sleepTimerRemaining! > 0;

    return AlertDialog(
      backgroundColor: sapphoSurfaceLight,
      title: Row(
        children: [
          const Icon(Icons.bedtime, color: sapphoStarFilled, size: 24),
          const SizedBox(width: 12),
          const Text('Sleep Timer', style: TextStyle(color: Colors.white)),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasActiveTimer)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: sapphoWarning.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Timer active',
                        style: TextStyle(
                          color: sapphoStarFilled,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        '${player.sleepTimerRemaining! ~/ 60}:${(player.sleepTimerRemaining! % 60).toString().padLeft(2, '0')} remaining',
                        style: TextStyle(
                          color: sapphoStarFilled.withValues(alpha: 0.8),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                  TextButton(
                    onPressed: () {
                      player.cancelSleepTimer();
                    },
                    child: const Text(
                      'Cancel',
                      style: TextStyle(color: sapphoError),
                    ),
                  ),
                ],
              ),
            ),
          ...timerOptions.map((option) {
            final (minutes, label) = option;
            return ListTile(
              title: Text(label, style: const TextStyle(color: Colors.white)),
              trailing: minutes == 0 && !hasActiveTimer
                  ? const Icon(
                      Icons.check_circle,
                      color: sapphoSuccess,
                      size: 18,
                    )
                  : null,
              onTap: () {
                player.setSleepTimer(minutes);
                Navigator.pop(context);
              },
            );
          }),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close', style: TextStyle(color: sapphoInfo)),
        ),
      ],
    );
  }
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
      return Center(
        child: Text(
          widget.text,
          style: widget.style,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
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

/// Dialog for casting to Chromecast devices
class _CastDialog extends StatefulWidget {
  final int audiobookId;
  final PlayerProvider player;

  const _CastDialog({required this.audiobookId, required this.player});

  @override
  State<_CastDialog> createState() => _CastDialogState();
}

class _CastDialogState extends State<_CastDialog> {
  final CastService _castService = CastService();

  @override
  void initState() {
    super.initState();
    _castService.addListener(_onCastUpdate);
    if (!_castService.isCasting) {
      _castService.startDiscovery();
    }
  }

  @override
  void dispose() {
    _castService.removeListener(_onCastUpdate);
    if (!_castService.isCasting) {
      _castService.stopDiscovery();
    }
    super.dispose();
  }

  void _onCastUpdate() {
    if (mounted) setState(() {});
  }

  Future<void> _connectAndCast(CastDevice device) async {
    debugPrint('_CastDialog: Tapped on device ${device.name}');
    final player = widget.player;
    final book = player.currentAudiobook;
    if (book == null) {
      debugPrint('_CastDialog: No audiobook loaded');
      return;
    }

    // Stop local playback BEFORE starting cast
    final currentPositionSeconds = player.currentPosition;
    debugPrint(
      '_CastDialog: Stopping local playback at position $currentPositionSeconds seconds',
    );
    await player.stop();

    debugPrint(
      '_CastDialog: Attempting to connect to ${device.host}:${device.port}',
    );
    final connected = await _castService.connectToDevice(device);
    debugPrint('_CastDialog: Connection result: $connected');
    if (!connected || !mounted) return;

    // Wait for the receiver to be ready (reduced from 2s)
    await Future.delayed(const Duration(milliseconds: 800));

    // Build the audio URL
    final serverUrl = player.serverUrl ?? '';
    final token = player.authToken ?? '';
    final audioUrl = '$serverUrl/api/audiobooks/${book.id}/stream?token=$token';
    final coverUrl = book.coverImage != null
        ? '$serverUrl/api/audiobooks/${book.id}/cover?token=$token'
        : null;

    debugPrint(
      '_CastDialog: Casting audio to device at position ${currentPositionSeconds * 1000}ms',
    );
    await _castService.castAudio(
      url: audioUrl,
      title: book.title,
      author: book.author,
      coverUrl: coverUrl,
      startPosition:
          currentPositionSeconds * 1000, // Convert seconds to milliseconds
    );

    if (mounted) Navigator.pop(context);
  }

  Future<void> _stopCasting() async {
    final player = widget.player;
    final book = player.currentAudiobook;

    // Get the current position from cast before stopping
    final castPosition =
        _castService.currentPosition ~/ 1000; // Convert ms to seconds
    debugPrint(
      '_CastDialog._stopCasting: Stopping cast at position $castPosition seconds',
    );

    _castService.stopCasting();
    _castService.disconnect();

    // Reload local playback at the cast position
    if (book != null) {
      debugPrint(
        '_CastDialog._stopCasting: Reloading local playback for ${book.title}',
      );
      // Small delay to let cast cleanup
      await Future.delayed(const Duration(milliseconds: 200));
      await player.loadAndPlay(book.id, startPosition: castPosition);
    }

    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    debugPrint(
      '_CastDialog build: isSearching=${_castService.isSearching}, devices=${_castService.devices.length}, isCasting=${_castService.isCasting}',
    );
    return AlertDialog(
      backgroundColor: sapphoSurfaceLight,
      title: Row(
        children: [
          Icon(
            _castService.isCasting ? Icons.cast_connected : Icons.cast,
            color: _castService.isCasting ? sapphoInfo : Colors.white,
            size: 24,
          ),
          const SizedBox(width: 12),
          Text(
            _castService.isCasting ? 'Casting' : 'Cast to Device',
            style: const TextStyle(color: Colors.white),
          ),
        ],
      ),
      content: SizedBox(
        width: double.maxFinite,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_castService.isCasting &&
                _castService.connectedDevice != null) ...[
              // Currently casting view
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: sapphoInfo.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.tv, color: sapphoInfo, size: 20),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _castService.connectedDevice!.name,
                            style: const TextStyle(
                              color: sapphoInfo,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Currently casting',
                      style: TextStyle(
                        color: sapphoInfo.withValues(alpha: 0.8),
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(height: 16),
                    // Cast playback controls
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          onPressed: () => _castService.skipBackward(10),
                          icon: const Icon(
                            Icons.replay_10,
                            color: Colors.white,
                          ),
                        ),
                        IconButton(
                          onPressed: () => _castService.togglePlayPause(),
                          icon: Icon(
                            _castService.isPlaying
                                ? Icons.pause
                                : Icons.play_arrow,
                            color: Colors.white,
                            size: 32,
                          ),
                        ),
                        IconButton(
                          onPressed: () => _castService.skipForward(10),
                          icon: const Icon(
                            Icons.forward_10,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Center(
                child: TextButton.icon(
                  onPressed: _stopCasting,
                  icon: const Icon(Icons.stop, color: sapphoError),
                  label: const Text(
                    'Stop Casting',
                    style: TextStyle(color: sapphoError),
                  ),
                ),
              ),
            ] else if (_castService.isConnecting) ...[
              // Connecting view
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Column(
                    children: [
                      CircularProgressIndicator(color: sapphoInfo),
                      SizedBox(height: 16),
                      Text(
                        'Connecting...',
                        style: TextStyle(color: Colors.white),
                      ),
                    ],
                  ),
                ),
              ),
            ] else ...[
              // Device discovery view
              if (_castService.error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(
                    _castService.error!,
                    style: const TextStyle(color: sapphoError, fontSize: 13),
                  ),
                ),
              if (_castService.isSearching)
                const Padding(
                  padding: EdgeInsets.only(bottom: 12),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: sapphoInfo,
                        ),
                      ),
                      SizedBox(width: 12),
                      Text(
                        'Searching for devices...',
                        style: TextStyle(color: sapphoTextMuted, fontSize: 13),
                      ),
                    ],
                  ),
                ),
              if (_castService.devices.isEmpty && !_castService.isSearching)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                    child: Column(
                      children: [
                        Icon(Icons.cast, color: sapphoTextMuted, size: 48),
                        SizedBox(height: 12),
                        Text(
                          'No devices found',
                          style: TextStyle(color: sapphoTextMuted),
                        ),
                        SizedBox(height: 4),
                        Text(
                          'Make sure your Chromecast is on\nthe same network',
                          style: TextStyle(
                            color: sapphoTextMuted,
                            fontSize: 12,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                )
              else
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 250),
                  child: ListView.builder(
                    shrinkWrap: true,
                    physics: const ClampingScrollPhysics(),
                    itemCount: _castService.devices.length,
                    itemBuilder: (context, index) {
                      final device = _castService.devices[index];
                      return InkWell(
                        onTap: () {
                          debugPrint('InkWell tapped: ${device.name}');
                          _connectAndCast(device);
                        },
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            vertical: 12,
                            horizontal: 8,
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.tv, color: sapphoIconDefault),
                              const SizedBox(width: 16),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      device.name,
                                      style: const TextStyle(
                                        color: Colors.white,
                                      ),
                                    ),
                                    Text(
                                      device.host,
                                      style: const TextStyle(
                                        color: sapphoTextMuted,
                                        fontSize: 12,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const Icon(
                                Icons.chevron_right,
                                color: sapphoTextMuted,
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
              if (!_castService.isSearching)
                Center(
                  child: TextButton.icon(
                    onPressed: () => _castService.startDiscovery(),
                    icon: const Icon(Icons.refresh, color: sapphoInfo),
                    label: const Text(
                      'Search Again',
                      style: TextStyle(color: sapphoInfo),
                    ),
                  ),
                ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close', style: TextStyle(color: sapphoInfo)),
        ),
      ],
    );
  }
}
