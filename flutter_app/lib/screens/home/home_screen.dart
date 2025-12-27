import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/audiobook.dart';
import '../../providers/auth_provider.dart';
import '../../theme/app_theme.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<Audiobook> _inProgress = [];
  List<Audiobook> _recent = [];
  List<Audiobook> _finished = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final api = context.read<AuthProvider>().apiService;
    try {
      final results = await Future.wait([
        api.getInProgress(),
        api.getRecent(),
        api.getFinished(),
      ]);
      setState(() {
        _inProgress = results[0];
        _recent = results[1];
        _finished = results[2];
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();

    if (_isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: _loadData,
        child: CustomScrollView(
          slivers: [
            // App bar with user avatar
            SliverAppBar(
              floating: true,
              title: const Text('Sappho'),
              actions: [
                PopupMenuButton<String>(
                  offset: const Offset(0, 48),
                  onSelected: (value) {
                    if (value == 'logout') {
                      auth.logout();
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'profile',
                      child: Row(
                        children: [
                          const Icon(Icons.person),
                          const SizedBox(width: 12),
                          Text(auth.user?.displayName ?? auth.user?.username ?? 'User'),
                        ],
                      ),
                    ),
                    const PopupMenuItem(
                      value: 'logout',
                      child: Row(
                        children: [
                          Icon(Icons.logout, color: SapphoColors.error),
                          SizedBox(width: 12),
                          Text('Logout'),
                        ],
                      ),
                    ),
                  ],
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: CircleAvatar(
                      backgroundColor: SapphoColors.primary,
                      child: Text(
                        (auth.user?.username ?? 'U')[0].toUpperCase(),
                        style: const TextStyle(color: Colors.white),
                      ),
                    ),
                  ),
                ),
              ],
            ),

            // Continue Listening section
            if (_inProgress.isNotEmpty) ...[
              _buildSectionHeader('Continue Listening'),
              _buildHorizontalList(_inProgress),
            ],

            // Recently Added section
            if (_recent.isNotEmpty) ...[
              _buildSectionHeader('Recently Added'),
              _buildHorizontalList(_recent),
            ],

            // Listen Again section
            if (_finished.isNotEmpty) ...[
              _buildSectionHeader('Listen Again'),
              _buildHorizontalList(_finished),
            ],

            // Bottom padding
            const SliverPadding(padding: EdgeInsets.only(bottom: 100)),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
        child: Text(
          title,
          style: const TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: SapphoColors.textPrimary,
          ),
        ),
      ),
    );
  }

  Widget _buildHorizontalList(List<Audiobook> books) {
    return SliverToBoxAdapter(
      child: SizedBox(
        height: 200,
        child: ListView.builder(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16),
          itemCount: books.length,
          itemBuilder: (context, index) {
            final book = books[index];
            return _BookCard(book: book, serverUrl: context.read<AuthProvider>().serverUrl);
          },
        ),
      ),
    );
  }
}

class _BookCard extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;

  const _BookCard({required this.book, this.serverUrl});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 130,
      margin: const EdgeInsets.only(right: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Cover image
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: AspectRatio(
              aspectRatio: 1,
              child: book.coverImage != null && serverUrl != null
                  ? CachedNetworkImage(
                      imageUrl: '$serverUrl/api/audiobooks/${book.id}/cover',
                      fit: BoxFit.cover,
                      placeholder: (context, url) => Container(
                        color: SapphoColors.surfaceLight,
                        child: const Center(child: CircularProgressIndicator(strokeWidth: 2)),
                      ),
                      errorWidget: (context, url, error) => _buildPlaceholder(),
                      httpHeaders: {
                        // Auth token would be added here via interceptor
                      },
                    )
                  : _buildPlaceholder(),
            ),
          ),
          const SizedBox(height: 8),

          // Title
          Text(
            book.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: SapphoColors.textPrimary,
            ),
          ),

          // Author
          if (book.author != null)
            Text(
              book.author!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 11,
                color: SapphoColors.textSecondary,
              ),
            ),

          // Progress bar
          if (book.progress != null && book.progress! > 0)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: LinearProgressIndicator(
                value: book.progress! / 100,
                backgroundColor: SapphoColors.progressTrack,
                valueColor: const AlwaysStoppedAnimation(SapphoColors.primary),
                minHeight: 3,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      color: SapphoColors.surfaceLight,
      child: Center(
        child: Text(
          book.title.substring(0, 2).toUpperCase(),
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.bold,
            color: SapphoColors.primary,
          ),
        ),
      ),
    );
  }
}
