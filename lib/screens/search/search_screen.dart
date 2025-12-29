import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/audiobook.dart';
import '../../providers/search_provider.dart';
import '../../theme/app_theme.dart';

/// Search screen matching Android SearchScreen.kt
class SearchScreen extends StatefulWidget {
  final Function(int)? onAudiobookTap;
  final Function(String)? onSeriesTap;
  final Function(String)? onAuthorTap;

  const SearchScreen({
    super.key,
    this.onAudiobookTap,
    this.onSeriesTap,
    this.onAuthorTap,
  });

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    // Auto-focus the search field
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<SearchProvider>(
      builder: (context, search, child) {
        return Column(
          children: [
            // Search bar
            Padding(
              padding: const EdgeInsets.all(16),
              child: Container(
                decoration: BoxDecoration(
                  color: sapphoSurfaceLight,
                  borderRadius: BorderRadius.circular(12),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 4,
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.search,
                      color: sapphoIconDefault,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        controller: _searchController,
                        focusNode: _focusNode,
                        style: const TextStyle(color: sapphoText, fontSize: 16),
                        decoration: const InputDecoration(
                          hintText: 'Search books, series, authors...',
                          hintStyle: TextStyle(color: sapphoTextMuted),
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(vertical: 12),
                        ),
                        onChanged: (value) => search.updateSearchQuery(value),
                      ),
                    ),
                    if (search.searchQuery.isNotEmpty)
                      IconButton(
                        onPressed: () {
                          _searchController.clear();
                          search.clearSearch();
                        },
                        icon: const Icon(
                          Icons.clear,
                          color: sapphoIconDefault,
                          size: 20,
                        ),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(
                          minWidth: 24,
                          minHeight: 24,
                        ),
                      ),
                  ],
                ),
              ),
            ),

            // Results
            Expanded(child: _buildContent(search)),
          ],
        );
      },
    );
  }

  Widget _buildContent(SearchProvider search) {
    if (search.isLoading) {
      return const Center(child: CircularProgressIndicator(color: sapphoInfo));
    }

    if (search.searchQuery.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.search, size: 64, color: sapphoProgressTrack),
            const SizedBox(height: 16),
            const Text(
              'Search for books, series, or authors',
              style: TextStyle(color: sapphoTextMuted, fontSize: 16),
            ),
          ],
        ),
      );
    }

    if (search.results.isEmpty) {
      return const Center(
        child: Text(
          'No results found',
          style: TextStyle(color: sapphoTextMuted, fontSize: 16),
        ),
      );
    }

    final bottomPadding = MediaQuery.of(context).padding.bottom;
    return ListView(
      padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 16),
      children: [
        // Books section
        if (search.results.books.isNotEmpty) ...[
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text(
              'Books',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: sapphoIconDefault,
                letterSpacing: 0.5,
              ),
            ),
          ),
          ...search.results.books.map(
            (book) => _SearchResultItem(
              book: book,
              serverUrl: search.serverUrl,
              authToken: search.authToken,
              onTap: () => widget.onAudiobookTap?.call(book.id),
            ),
          ),
        ],

        // Series section
        if (search.results.series.isNotEmpty) ...[
          const Padding(
            padding: EdgeInsets.only(top: 16, bottom: 8),
            child: Text(
              'Series',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: sapphoIconDefault,
                letterSpacing: 0.5,
              ),
            ),
          ),
          ...search.results.series.map(
            (series) => _SeriesResultItem(
              series: series,
              onTap: () => widget.onSeriesTap?.call(series),
            ),
          ),
        ],

        // Authors section
        if (search.results.authors.isNotEmpty) ...[
          const Padding(
            padding: EdgeInsets.only(top: 16, bottom: 8),
            child: Text(
              'Authors',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: sapphoIconDefault,
                letterSpacing: 0.5,
              ),
            ),
          ),
          ...search.results.authors.map(
            (author) => _AuthorResultItem(
              author: author,
              onTap: () => widget.onAuthorTap?.call(author),
            ),
          ),
        ],

        const SizedBox(height: 16),
      ],
    );
  }
}

/// Book search result item
class _SearchResultItem extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback? onTap;

  const _SearchResultItem({
    required this.book,
    required this.serverUrl,
    this.authToken,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            // Cover image
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(6),
              ),
              clipBehavior: Clip.antiAlias,
              child: book.coverImage != null && serverUrl != null
                  ? CachedNetworkImage(
                      imageUrl: '$serverUrl/api/audiobooks/${book.id}/cover',
                      fit: BoxFit.cover,
                      memCacheWidth: 96,
                      memCacheHeight: 96,
                      fadeInDuration: Duration.zero,
                      fadeOutDuration: Duration.zero,
                      httpHeaders: authToken != null
                          ? {'Authorization': 'Bearer $authToken'}
                          : null,
                      placeholder: (_, __) => Center(
                        child: Text(
                          book.title.isNotEmpty
                              ? book.title[0].toUpperCase()
                              : 'B',
                          style: const TextStyle(
                            color: sapphoInfo,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      errorWidget: (_, __, ___) => Center(
                        child: Text(
                          book.title.isNotEmpty
                              ? book.title[0].toUpperCase()
                              : 'B',
                          style: const TextStyle(
                            color: sapphoInfo,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    )
                  : Center(
                      child: Text(
                        book.title.isNotEmpty
                            ? book.title[0].toUpperCase()
                            : 'B',
                        style: const TextStyle(
                          color: sapphoInfo,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
            ),

            const SizedBox(width: 12),

            // Book info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    book.title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: sapphoText,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    _buildSubtitle(),
                    style: const TextStyle(
                      fontSize: 12,
                      color: sapphoIconDefault,
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
    );
  }

  String _buildSubtitle() {
    final parts = <String>[];
    if (book.author != null) parts.add(book.author!);
    if (book.series != null) parts.add(book.series!);
    return parts.join(' - ');
  }
}

/// Series search result item
class _SeriesResultItem extends StatelessWidget {
  final String series;
  final VoidCallback? onTap;

  const _SeriesResultItem({required this.series, this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Icon(
                Icons.menu_book,
                color: sapphoIconDefault,
                size: 24,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                series,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: sapphoText,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Author search result item
class _AuthorResultItem extends StatelessWidget {
  final String author;
  final VoidCallback? onTap;

  const _AuthorResultItem({required this.author, this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Icon(
                Icons.person,
                color: sapphoIconDefault,
                size: 24,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                author,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: sapphoText,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
