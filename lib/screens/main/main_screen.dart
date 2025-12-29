import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/user.dart';
import '../../providers/auth_provider.dart';
import '../../providers/connectivity_provider.dart';
import '../../providers/detail_provider.dart';
import '../../providers/download_provider.dart';
import '../../providers/library_provider.dart';
import '../../providers/player_provider.dart';
import '../../services/download_service.dart';
import '../../theme/app_theme.dart';
import '../home/home_screen.dart';
import '../library/library_screen.dart';
import '../search/search_screen.dart';
import '../detail/audiobook_detail_screen.dart';
import '../player/player_screen.dart';
import '../player/minimized_player_bar.dart';
import '../profile/profile_screen.dart';
import '../admin/admin_screen.dart';
import '../reading_list/reading_list_screen.dart';
import '../collections/collections_screen.dart';
import '../collections/collection_detail_screen.dart';
import '../../services/api_service.dart';
import '../../services/auth_repository.dart';

/// Main screen with TopBar and navigation
/// Matches Android MainScreen.kt
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

/// Navigation context for tracking where user came from
class _NavigationContext {
  final int tabIndex;
  final bool showCollections;
  final int? collectionId;
  final bool showReadingList;
  final bool showProfileScreen;

  _NavigationContext({
    required this.tabIndex,
    this.showCollections = false,
    this.collectionId,
    this.showReadingList = false,
    this.showProfileScreen = false,
  });
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;
  int? _selectedAudiobookId;
  bool _showPlayerScreen = false;
  int? _playerAudiobookId; // Track which audiobook we're playing
  bool _showProfileScreen = false;
  bool _showAdminScreen = false;
  bool _showReadingListScreen = false;
  bool _showCollectionsScreen = false;
  int? _selectedCollectionId;

  // Navigation stack to track where user came from
  final List<_NavigationContext> _navigationStack = [];

  void _navigateToDetail(int audiobookId) {
    // Push current context to stack before navigating
    _navigationStack.add(
      _NavigationContext(
        tabIndex: _currentIndex,
        showCollections: _showCollectionsScreen,
        collectionId: _selectedCollectionId,
        showReadingList: _showReadingListScreen,
        showProfileScreen: _showProfileScreen,
      ),
    );
    setState(() {
      _selectedAudiobookId = audiobookId;
    });
  }

  void _navigateBack() {
    setState(() {
      _selectedAudiobookId = null;
      // Restore previous navigation context if available
      if (_navigationStack.isNotEmpty) {
        final navContext = _navigationStack.removeLast();
        _currentIndex = navContext.tabIndex;
        _showCollectionsScreen = navContext.showCollections;
        _selectedCollectionId = navContext.collectionId;
        _showReadingListScreen = navContext.showReadingList;
        _showProfileScreen = navContext.showProfileScreen;
      }
    });
    // Clear the detail provider
    context.read<DetailProvider>().clear();
    // Refresh library data to pick up any changes (ratings, progress, etc.)
    context.read<LibraryProvider>().refresh();
  }

  /// Check if there's a profile context in the navigation stack
  bool _hasProfileInNavigationStack() {
    return _navigationStack.any((ctx) => ctx.showProfileScreen);
  }

  /// Return to profile by popping all contexts until we reach profile
  void _returnToProfileFromStack() {
    setState(() {
      // Pop contexts until we find one with profile
      while (_navigationStack.isNotEmpty) {
        final navContext = _navigationStack.removeLast();
        if (navContext.showProfileScreen) {
          _currentIndex = navContext.tabIndex;
          _showProfileScreen = true;
          _showCollectionsScreen = false;
          _showReadingListScreen = false;
          break;
        }
      }
    });
    // Clear any library selection state
    context.read<LibraryProvider>().clearSelection();
  }

  void _navigateToPlayer(int audiobookId, int? startPosition) {
    final player = context.read<PlayerProvider>();
    player.loadAndPlay(audiobookId, startPosition: startPosition ?? 0);
    setState(() {
      _showPlayerScreen = true;
      _playerAudiobookId = audiobookId;
    });
  }

  void _minimizePlayer() {
    setState(() {
      _showPlayerScreen = false;
    });
  }

  void _expandPlayer() {
    final player = context.read<PlayerProvider>();
    setState(() {
      _showPlayerScreen = true;
      _playerAudiobookId = player.currentAudiobook?.id;
    });
  }

  void _clearAllScreens() {
    _showProfileScreen = false;
    _showAdminScreen = false;
    _showReadingListScreen = false;
    _showCollectionsScreen = false;
    _selectedCollectionId = null;
  }

  void _showProfile() {
    setState(() {
      _clearAllScreens();
      _showProfileScreen = true;
    });
  }

  void _hideProfile() {
    setState(() {
      _showProfileScreen = false;
    });
  }

  void _showAdmin() {
    setState(() {
      _clearAllScreens();
      _showAdminScreen = true;
    });
  }

  void _hideAdmin() {
    setState(() {
      _showAdminScreen = false;
    });
  }

  void _showReadingList() {
    setState(() {
      _clearAllScreens();
      _showReadingListScreen = true;
    });
    // Refresh reading list from server
    context.read<LibraryProvider>().refreshReadingList();
  }

  void _hideReadingList() {
    setState(() {
      _showReadingListScreen = false;
    });
  }

  void _showCollections() {
    setState(() {
      _clearAllScreens();
      _showCollectionsScreen = true;
    });
  }

  void _hideCollections() {
    setState(() {
      _showCollectionsScreen = false;
    });
  }

  void _showCollectionDetail(int collectionId) {
    setState(() {
      _selectedCollectionId = collectionId;
      // Keep showCollectionsScreen true so back navigation works
      _showCollectionsScreen = true;
    });
  }

  void _hideCollectionDetail() {
    setState(() {
      _selectedCollectionId = null;
      // Stay in collections screen when going back from collection detail
      _showCollectionsScreen = true;
    });
  }

  void _showDownloadsDialog() {
    if (!mounted) return;

    // Use Provider.of with listen: false to get the provider from State's context
    final downloadProvider = Provider.of<DownloadProvider>(
      context,
      listen: false,
    );

    showModalBottomSheet(
      context: context,
      backgroundColor: sapphoSurface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (sheetCtx) => DraggableScrollableSheet(
        initialChildSize: 0.6,
        minChildSize: 0.3,
        maxChildSize: 0.9,
        expand: false,
        builder: (_, scrollController) => ListenableBuilder(
          listenable: downloadProvider,
          builder: (context, _) {
            // Show loading state
            if (!downloadProvider.isInitialized) {
              return const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: CircularProgressIndicator(color: sapphoInfo),
                ),
              );
            }

            // Show error state
            if (downloadProvider.error != null) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.error_outline,
                        size: 48,
                        color: sapphoError,
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        'Failed to load downloads',
                        style: TextStyle(color: Colors.white, fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        downloadProvider.error!,
                        style: const TextStyle(
                          color: sapphoTextMuted,
                          fontSize: 13,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => downloadProvider.refresh(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: sapphoInfo,
                        ),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              );
            }

            final downloads = downloadProvider.downloads;
            final completedDownloads = downloadProvider.completedDownloads;

            return Column(
              children: [
                // Handle bar
                Container(
                  margin: const EdgeInsets.only(top: 12),
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: sapphoProgressTrack,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                // Header
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Downloads',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Text(
                            '${completedDownloads.length} downloaded • ${downloadProvider.formattedStorageUsed}',
                            style: const TextStyle(
                              color: sapphoTextMuted,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                      if (completedDownloads.isNotEmpty)
                        TextButton(
                          onPressed: () => _confirmClearAllDownloads(
                            sheetCtx,
                            downloadProvider,
                          ),
                          child: const Text(
                            'Clear All',
                            style: TextStyle(color: sapphoError),
                          ),
                        ),
                    ],
                  ),
                ),
                const Divider(color: sapphoSurfaceBorder, height: 1),
                // Downloads list
                Expanded(
                  child: downloads.isEmpty
                      ? const Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.download_outlined,
                                size: 48,
                                color: sapphoTextMuted,
                              ),
                              SizedBox(height: 12),
                              Text(
                                'No downloads yet',
                                style: TextStyle(
                                  color: sapphoTextMuted,
                                  fontSize: 16,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Download audiobooks to listen offline',
                                style: TextStyle(
                                  color: sapphoTextMuted,
                                  fontSize: 13,
                                ),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          controller: scrollController,
                          itemCount: downloads.length,
                          itemBuilder: (context, index) {
                            final download = downloads[index];
                            return _DownloadListItem(
                              download: download,
                              progress: downloadProvider.getProgress(
                                download.audiobookId,
                              ),
                              onDelete: () async {
                                await downloadProvider.deleteDownload(
                                  download.audiobookId,
                                );
                              },
                              onCancel: () async {
                                await downloadProvider.cancelDownload(
                                  download.audiobookId,
                                );
                              },
                            );
                          },
                        ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  void _confirmClearAllDownloads(
    BuildContext context,
    DownloadProvider provider,
  ) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Clear All Downloads?',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'This will remove all downloaded audiobooks from your device.',
          style: TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              for (final download in provider.completedDownloads) {
                await provider.deleteDownload(download.audiobookId);
              }
            },
            child: const Text(
              'Clear All',
              style: TextStyle(color: sapphoError),
            ),
          ),
        ],
      ),
    );
  }

  /// Builds the content area based on current navigation state
  Widget _buildContent(AuthProvider auth, PlayerProvider player) {
    // Show profile screen if requested
    if (_showProfileScreen) {
      return ProfileScreen(
        onBack: _hideProfile,
        onAuthorTap: (author) {
          // Push profile context to stack so back returns here
          _navigationStack.add(
            _NavigationContext(
              tabIndex: _currentIndex,
              showProfileScreen: true,
            ),
          );
          setState(() {
            _showProfileScreen = false;
            _currentIndex = 1; // Library tab
          });
          final libraryProvider = context.read<LibraryProvider>();
          libraryProvider.selectAuthor(author);
        },
        onGenreTap: (genre) {
          // Push profile context to stack so back returns here
          _navigationStack.add(
            _NavigationContext(
              tabIndex: _currentIndex,
              showProfileScreen: true,
            ),
          );
          setState(() {
            _showProfileScreen = false;
            _currentIndex = 1; // Library tab
          });
          final libraryProvider = context.read<LibraryProvider>();
          libraryProvider.selectGenre(genre);
        },
        onAudiobookTap: (audiobookId) {
          // _navigateToDetail will push profile context to stack automatically
          _navigateToDetail(audiobookId);
        },
      );
    }

    // Show admin screen if requested
    if (_showAdminScreen) {
      return AdminScreen(onBack: _hideAdmin);
    }

    // Show detail screen if an audiobook is selected (check first before other screens)
    if (_selectedAudiobookId != null) {
      return AudiobookDetailScreen(
        audiobookId: _selectedAudiobookId!,
        onBackClick: _navigateBack,
        onPlayClick: (id, position) {
          _navigateToPlayer(id, position);
        },
        onAuthorClick: (author) {
          setState(() {
            _selectedAudiobookId = null;
            _currentIndex = 1; // Library tab
          });
          // Switch to Authors view in library
          final libraryProvider = context.read<LibraryProvider>();
          libraryProvider.selectAuthor(author);
        },
        onSeriesClick: (series) {
          setState(() {
            _selectedAudiobookId = null;
            _currentIndex = 1; // Library tab
          });
          // Switch to Series view in library
          final libraryProvider = context.read<LibraryProvider>();
          libraryProvider.selectSeries(series);
        },
        onGenreClick: (genre) {
          setState(() {
            _selectedAudiobookId = null;
            _currentIndex = 1; // Library tab
          });
          // Switch to Genres view in library
          final libraryProvider = context.read<LibraryProvider>();
          libraryProvider.selectGenre(genre);
        },
      );
    }

    // Show reading list screen if requested
    if (_showReadingListScreen) {
      return ReadingListScreen(
        onBack: _hideReadingList,
        onAudiobookTap: (id) {
          // Don't hide reading list - navigateToDetail will push the context
          _navigateToDetail(id);
        },
      );
    }

    // Show collections screen if requested
    if (_showCollectionsScreen && _selectedCollectionId == null) {
      return CollectionsScreen(
        onBack: _hideCollections,
        onCollectionTap: _showCollectionDetail,
      );
    }

    // Show collection detail screen if requested
    if (_selectedCollectionId != null) {
      final api = context.read<ApiService>();
      final authRepo = context.read<AuthRepository>();
      return CollectionDetailScreen(
        collectionId: _selectedCollectionId!,
        api: api,
        authRepository: authRepo,
        onBack: _hideCollectionDetail,
        onBookTap: (id) {
          // Don't hide collection detail - navigateToDetail will push the context
          _navigateToDetail(id);
        },
      );
    }

    // Default: Show main content tabs
    return IndexedStack(
      index: _currentIndex,
      children: [
        HomeScreen(
          onAudiobookTap: _navigateToDetail,
          onPlay: _navigateToPlayer,
        ),
        LibraryScreen(
          onAudiobookTap: _navigateToDetail,
          onCollectionsTap: _showCollections,
          onReadingListTap: _showReadingList,
          showBackToProfile: _hasProfileInNavigationStack(),
          onBackToProfile: _returnToProfileFromStack,
        ),
        SearchScreen(onAudiobookTap: _navigateToDetail),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final player = context.watch<PlayerProvider>();

    // Show player screen if requested (full screen, no wrapper)
    if (_showPlayerScreen && _playerAudiobookId != null) {
      return PlayerScreen(
        audiobookId: _playerAudiobookId!,
        fromMinimized: true,
        onMinimize: _minimizePlayer,
      );
    }

    return Consumer<ConnectivityProvider>(
      builder: (context, connectivity, child) {
        return Scaffold(
          backgroundColor: sapphoBackground,
          body: Column(
            children: [
              // TopBar - always visible
              _TopBar(
                user: auth.user,
                serverUrl: auth.serverUrl,
                authToken: auth.token,
                currentIndex: _currentIndex,
                onNavTap: (index) {
                  // Clear any sub-screens when tapping nav
                  setState(() {
                    _currentIndex = index;
                    _selectedAudiobookId = null;
                    _showProfileScreen = false;
                    _showAdminScreen = false;
                    _showReadingListScreen = false;
                    _showCollectionsScreen = false;
                    _selectedCollectionId = null;
                  });
                  // Reset library to main categories view when Library tab is tapped
                  if (index == 1) {
                    final library = context.read<LibraryProvider>();
                    library.setViewIndex(0);
                    library.clearSelection();
                  }
                },
                onLogout: () {
                  auth.logout();
                },
                onProfileTap: _showProfile,
                onAdminTap: _showAdmin,
                onDownloadsTap: _showDownloadsDialog,
              ),

              // Offline banner
              if (connectivity.isOffline)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    vertical: 8,
                    horizontal: 16,
                  ),
                  color: sapphoWarning,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: const [
                      Icon(Icons.cloud_off, color: Colors.black, size: 18),
                      SizedBox(width: 8),
                      Text(
                        'You\'re offline',
                        style: TextStyle(
                          color: Colors.black,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),

              // Content - sub-screens or main tabs
              Expanded(child: _buildContent(auth, player)),

              // Minimized Player Bar - always visible when playing
              if (player.hasAudiobook && !_showPlayerScreen)
                MinimizedPlayerBar(onExpand: _expandPlayer),
            ],
          ),
        );
      },
    );
  }
}

class _TopBar extends StatefulWidget {
  final User? user;
  final String? serverUrl;
  final String? authToken;
  final int currentIndex;
  final Function(int) onNavTap;
  final VoidCallback onLogout;
  final VoidCallback? onProfileTap;
  final VoidCallback? onAdminTap;
  final VoidCallback? onDownloadsTap;

  const _TopBar({
    required this.user,
    required this.serverUrl,
    this.authToken,
    required this.currentIndex,
    required this.onNavTap,
    required this.onLogout,
    this.onProfileTap,
    this.onAdminTap,
    this.onDownloadsTap,
  });

  @override
  State<_TopBar> createState() => _TopBarState();
}

class _TopBarState extends State<_TopBar> {
  final GlobalKey _avatarKey = GlobalKey();
  String? _serverVersion;

  @override
  void initState() {
    super.initState();
    _loadServerVersion();
  }

  Future<void> _loadServerVersion() async {
    try {
      final api = Provider.of<ApiService>(context, listen: false);
      final health = await api.getHealth();
      if (mounted) {
        setState(() {
          _serverVersion = health['version']?.toString();
        });
      }
    } catch (e) {
      debugPrint('Failed to load server version: $e');
    }
  }

  void _showUserMenu() {
    final RenderBox? renderBox =
        _avatarKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null) return;

    final position = renderBox.localToGlobal(Offset.zero);
    final size = renderBox.size;

    // Note: PopupMenuItem.onTap is called AFTER the menu automatically closes,
    // so we don't need to call Navigator.pop ourselves
    final items = <PopupMenuEntry<dynamic>>[
      _buildMenuItem(Icons.person, 'Profile', () {
        widget.onProfileTap?.call();
      }),
      _buildMenuItem(Icons.download, 'Downloads', () {
        widget.onDownloadsTap?.call();
      }),
    ];

    if (widget.user?.isAdminUser == true) {
      items.add(
        _buildMenuItem(Icons.admin_panel_settings, 'Admin', () {
          widget.onAdminTap?.call();
        }),
      );
    }

    items.addAll([
      _buildMenuItem(Icons.exit_to_app, 'Logout', () {
        widget.onLogout();
      }),
      const PopupMenuDivider(),
      PopupMenuItem<dynamic>(
        enabled: false,
        height: 24,
        child: Text(
          'App: 1.0.0',
          style: Theme.of(
            context,
          ).textTheme.labelSmall?.copyWith(color: sapphoTextMuted),
        ),
      ),
      if (_serverVersion != null)
        PopupMenuItem<dynamic>(
          enabled: false,
          height: 24,
          child: Text(
            'Server: $_serverVersion',
            style: Theme.of(
              context,
            ).textTheme.labelSmall?.copyWith(color: sapphoTextMuted),
          ),
        ),
    ]);

    showMenu<dynamic>(
      context: context,
      position: RelativeRect.fromLTRB(
        position.dx - 160 + size.width, // Position left of avatar
        position.dy + size.height + 8, // Below avatar
        position.dx + size.width,
        0,
      ),
      color: sapphoSurface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: sapphoSurfaceBorder),
      ),
      items: items,
    );
  }

  PopupMenuItem<dynamic> _buildMenuItem(
    IconData icon,
    String text,
    VoidCallback onTap,
  ) {
    return PopupMenuItem<dynamic>(
      onTap: onTap,
      child: Row(
        children: [
          Icon(icon, size: 20, color: sapphoIconDefault),
          const SizedBox(width: 12),
          Text(text, style: const TextStyle(color: sapphoText)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      color: sapphoSurface,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Logo
              GestureDetector(
                onTap: () => widget.onNavTap(0),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Image.asset(
                    'assets/sappho_icon.png',
                    width: 48,
                    height: 48,
                    errorBuilder: (context, error, stackTrace) {
                      return Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: sapphoSurfaceLight,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Center(
                          child: Text(
                            'S',
                            style: TextStyle(
                              color: sapphoPrimary,
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),

              // Center Navigation
              Row(
                children: [
                  _NavIcon(
                    icon: Icons.home,
                    label: 'Home',
                    isSelected: widget.currentIndex == 0,
                    onTap: () => widget.onNavTap(0),
                  ),
                  const SizedBox(width: 8),
                  _NavIcon(
                    icon: Icons.menu_book,
                    label: 'Library',
                    isSelected: widget.currentIndex == 1,
                    onTap: () => widget.onNavTap(1),
                  ),
                  const SizedBox(width: 8),
                  _NavIcon(
                    icon: Icons.search,
                    label: 'Search',
                    isSelected: widget.currentIndex == 2,
                    onTap: () => widget.onNavTap(2),
                  ),
                ],
              ),

              // User Avatar
              GestureDetector(
                key: _avatarKey,
                onTap: _showUserMenu,
                child: Container(
                  width: 40,
                  height: 40,
                  decoration: const BoxDecoration(
                    color: sapphoInfo,
                    shape: BoxShape.circle,
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: _buildAvatar(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAvatar() {
    final avatarPath = widget.user?.avatar;
    final username = widget.user?.username ?? 'U';

    // Only attempt to load avatar if we have all required auth info
    if (avatarPath != null &&
        widget.serverUrl != null &&
        widget.authToken != null) {
      // Include token hash in URL to bust cache when auth changes
      final tokenHash = widget.authToken.hashCode;
      final avatarUrl =
          '${widget.serverUrl}/api/profile/avatar?v=${avatarPath.hashCode}&t=$tokenHash';
      return CachedNetworkImage(
        imageUrl: avatarUrl,
        fit: BoxFit.cover,
        width: 40,
        height: 40,
        memCacheWidth: 80,
        memCacheHeight: 80,
        fadeInDuration: Duration.zero,
        fadeOutDuration: Duration.zero,
        httpHeaders: {'Authorization': 'Bearer ${widget.authToken}'},
        // Use cacheKey that includes token to prevent stale cache
        cacheKey: 'avatar_${avatarPath.hashCode}_$tokenHash',
        placeholder: (context, url) => _buildInitial(username),
        errorWidget: (context, url, error) {
          debugPrint('Avatar error: $error');
          return _buildInitial(username);
        },
      );
    }

    return _buildInitial(username);
  }

  Widget _buildInitial(String username) {
    return Center(
      child: Text(
        username.isNotEmpty ? username[0].toUpperCase() : 'U',
        style: const TextStyle(
          color: Colors.white,
          fontSize: 18,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _NavIcon extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  const _NavIcon({
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onTap,
      icon: Icon(
        icon,
        color: isSelected ? sapphoInfo : sapphoIconDefault,
        size: 24,
      ),
      tooltip: label,
    );
  }
}

/// Download list item for the downloads dialog
class _DownloadListItem extends StatelessWidget {
  final DownloadedAudiobook download;
  final double progress;
  final VoidCallback onDelete;
  final VoidCallback onCancel;

  const _DownloadListItem({
    required this.download,
    required this.progress,
    required this.onDelete,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    final isDownloading = download.status == DownloadStatus.downloading;
    final isPending = download.status == DownloadStatus.pending;
    final isCompleted = download.status == DownloadStatus.completed;
    final isFailed = download.status == DownloadStatus.failed;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: sapphoSurfaceBorder, width: 0.5),
        ),
      ),
      child: Row(
        children: [
          // Cover image
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: download.coverPath != null
                ? Image.file(
                    File(download.coverPath!),
                    width: 48,
                    height: 48,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => _buildCoverPlaceholder(),
                  )
                : _buildCoverPlaceholder(),
          ),
          const SizedBox(width: 12),
          // Info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  download.title,
                  style: const TextStyle(color: Colors.white, fontSize: 14),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                if (download.author != null)
                  Text(
                    download.author!,
                    style: const TextStyle(
                      color: sapphoTextMuted,
                      fontSize: 12,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                const SizedBox(height: 4),
                // Status
                if (isDownloading || isPending)
                  Row(
                    children: [
                      Expanded(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(2),
                          child: LinearProgressIndicator(
                            value: isDownloading ? progress : null,
                            backgroundColor: sapphoProgressTrack,
                            valueColor: const AlwaysStoppedAnimation(
                              sapphoInfo,
                            ),
                            minHeight: 4,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        isDownloading
                            ? '${(progress * 100).toInt()}%'
                            : 'Waiting...',
                        style: const TextStyle(
                          color: sapphoTextMuted,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  )
                else if (isCompleted)
                  Row(
                    children: [
                      const Icon(
                        Icons.check_circle,
                        color: sapphoSuccess,
                        size: 14,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        DownloadService.formatBytes(download.totalBytes),
                        style: const TextStyle(
                          color: sapphoTextMuted,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  )
                else if (isFailed)
                  Row(
                    children: [
                      const Icon(Icons.error, color: sapphoError, size: 14),
                      const SizedBox(width: 4),
                      Text(
                        download.error ?? 'Download failed',
                        style: const TextStyle(
                          color: sapphoError,
                          fontSize: 11,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
              ],
            ),
          ),
          // Action button
          IconButton(
            onPressed: (isDownloading || isPending) ? onCancel : onDelete,
            icon: Icon(
              (isDownloading || isPending) ? Icons.close : Icons.delete_outline,
              color: (isDownloading || isPending)
                  ? sapphoTextMuted
                  : sapphoError,
              size: 20,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCoverPlaceholder() {
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Text(
          download.title.isNotEmpty ? download.title[0].toUpperCase() : 'A',
          style: const TextStyle(color: sapphoTextMuted, fontSize: 18),
        ),
      ),
    );
  }
}
