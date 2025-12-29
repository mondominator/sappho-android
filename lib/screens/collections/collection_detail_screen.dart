import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/audiobook.dart';
import '../../models/collection.dart';
import '../../services/api_service.dart';
import '../../services/auth_repository.dart';
import '../../theme/app_theme.dart';

/// Collection detail screen matching Android CollectionDetailScreen.kt
class CollectionDetailScreen extends StatefulWidget {
  final int collectionId;
  final ApiService api;
  final AuthRepository authRepository;
  final Function(int)? onBookTap;
  final VoidCallback? onBack;

  const CollectionDetailScreen({
    super.key,
    required this.collectionId,
    required this.api,
    required this.authRepository,
    this.onBookTap,
    this.onBack,
  });

  @override
  State<CollectionDetailScreen> createState() => _CollectionDetailScreenState();
}

class _CollectionDetailScreenState extends State<CollectionDetailScreen> {
  CollectionDetail? _collection;
  bool _isLoading = true;
  bool _isEditMode = false;
  Set<int> _selectedBooks = {};
  bool _isSaving = false;
  String? _error;
  String? _serverUrl;
  String? _authToken;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadCollection();
    });
  }

  Future<void> _loadCollection() async {
    if (!mounted) return;
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      _serverUrl = await widget.authRepository.getServerUrl();
      _authToken = await widget.authRepository.getToken();
      final collection = await widget.api.getCollection(widget.collectionId);
      setState(() {
        _collection = collection;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  void _exitEditMode() {
    setState(() {
      _isEditMode = false;
      _selectedBooks = {};
    });
  }

  void _handleBack() {
    if (_isEditMode) {
      _exitEditMode();
    } else {
      widget.onBack?.call();
    }
  }

  Future<void> _removeSelectedBooks() async {
    if (_selectedBooks.isEmpty) return;

    final count = _selectedBooks.length;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Remove Books',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          count == 1
              ? 'Are you sure you want to remove this book from the collection?'
              : 'Are you sure you want to remove $count books from this collection?',
          style: const TextStyle(color: sapphoTextLight),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text(
              'Cancel',
              style: TextStyle(color: sapphoIconDefault),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Remove', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      for (final bookId in _selectedBooks) {
        try {
          await widget.api.removeFromCollection(widget.collectionId, bookId);
        } catch (e) {
          debugPrint('Failed to remove book $bookId: $e');
        }
      }
      if (mounted) {
        _exitEditMode();
        _loadCollection();
      }
    }
  }

  void _showEditDialog() {
    if (_collection == null) return;

    final nameController = TextEditingController(text: _collection!.name);
    final descController = TextEditingController(
      text: _collection!.description ?? '',
    );
    bool isPublic = _collection!.isPublic == 1;

    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          backgroundColor: sapphoSurface,
          title: const Text(
            'Edit Collection',
            style: TextStyle(color: Colors.white),
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Name',
                    labelStyle: TextStyle(color: sapphoIconDefault),
                    enabledBorder: OutlineInputBorder(
                      borderSide: BorderSide(color: sapphoProgressTrack),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderSide: BorderSide(color: sapphoInfo),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: descController,
                  style: const TextStyle(color: Colors.white),
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Description (optional)',
                    labelStyle: TextStyle(color: sapphoIconDefault),
                    enabledBorder: OutlineInputBorder(
                      borderSide: BorderSide(color: sapphoProgressTrack),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderSide: BorderSide(color: sapphoInfo),
                    ),
                  ),
                ),
                if (_collection!.isOwner == 1) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: sapphoProgressTrack.withValues(alpha: 0.5),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Public Collection',
                                style: TextStyle(color: Colors.white),
                              ),
                              Text(
                                isPublic
                                    ? 'Anyone can view and add books'
                                    : 'Only you can see this collection',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: sapphoIconDefault,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Switch(
                          value: isPublic,
                          onChanged: (value) =>
                              setDialogState(() => isPublic = value),
                          activeColor: sapphoSuccess,
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text(
                'Cancel',
                style: TextStyle(color: sapphoIconDefault),
              ),
            ),
            ElevatedButton(
              onPressed: _isSaving
                  ? null
                  : () async {
                      if (nameController.text.isEmpty) return;
                      setState(() => _isSaving = true);
                      try {
                        await widget.api.updateCollection(
                          widget.collectionId,
                          nameController.text,
                          description: descController.text.isEmpty
                              ? null
                              : descController.text,
                          isPublic: isPublic,
                        );
                        if (mounted) {
                          Navigator.pop(dialogContext);
                          _loadCollection();
                        }
                      } catch (e) {
                        debugPrint('Failed to update collection: $e');
                      } finally {
                        if (mounted) setState(() => _isSaving = false);
                      }
                    },
              style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              child: _isSaving
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: Column(
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                IconButton(
                  onPressed: _handleBack,
                  icon: Icon(
                    _isEditMode ? Icons.close : Icons.arrow_back,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _isEditMode
                            ? '${_selectedBooks.length} selected'
                            : (_collection?.name ?? 'Collection'),
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (!_isEditMode && _collection?.description != null)
                        Text(
                          _collection!.description!,
                          style: const TextStyle(
                            fontSize: 14,
                            color: sapphoIconDefault,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
                if (_isEditMode && _selectedBooks.isNotEmpty)
                  IconButton(
                    onPressed: _removeSelectedBooks,
                    icon: Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        color: sapphoError,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.delete,
                        color: Colors.white,
                        size: 20,
                      ),
                    ),
                  )
                else if (!_isEditMode)
                  IconButton(
                    onPressed: _showEditDialog,
                    icon: Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        color: sapphoProgressTrack,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.edit,
                        color: Colors.white,
                        size: 20,
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // Content
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: sapphoInfo),
                  )
                : _error != null
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          _error!,
                          style: const TextStyle(color: sapphoError),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          onPressed: _loadCollection,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: sapphoInfo,
                          ),
                          child: const Text('Retry'),
                        ),
                      ],
                    ),
                  )
                : _collection?.books.isEmpty == true
                ? _EmptyState()
                : _BooksGrid(
                    books: _collection!.books,
                    serverUrl: _serverUrl,
                    authToken: _authToken,
                    isEditMode: _isEditMode,
                    selectedBooks: _selectedBooks,
                    onBookTap: (id) {
                      if (_isEditMode) {
                        setState(() {
                          if (_selectedBooks.contains(id)) {
                            _selectedBooks.remove(id);
                          } else {
                            _selectedBooks.add(id);
                          }
                        });
                      } else {
                        widget.onBookTap?.call(id);
                      }
                    },
                    onLongPress: (id) {
                      if (!_isEditMode) {
                        setState(() {
                          _isEditMode = true;
                          _selectedBooks = {id};
                        });
                      }
                    },
                    bottomPadding: bottomPadding,
                  ),
          ),
        ],
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
          const Icon(Icons.library_books, size: 64, color: sapphoInfo),
          const SizedBox(height: 16),
          const Text(
            'No books in this collection',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Add books from the book detail page',
            style: TextStyle(fontSize: 14, color: sapphoIconDefault),
          ),
        ],
      ),
    );
  }
}

class _BooksGrid extends StatelessWidget {
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final bool isEditMode;
  final Set<int> selectedBooks;
  final Function(int) onBookTap;
  final Function(int) onLongPress;
  final double bottomPadding;

  const _BooksGrid({
    required this.books,
    this.serverUrl,
    this.authToken,
    required this.isEditMode,
    required this.selectedBooks,
    required this.onBookTap,
    required this.onLongPress,
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
      itemCount: books.length,
      itemBuilder: (context, index) {
        final book = books[index];
        final isSelected = selectedBooks.contains(book.id);

        return _BookItem(
          book: book,
          serverUrl: serverUrl,
          authToken: authToken,
          isEditMode: isEditMode,
          isSelected: isSelected,
          onTap: () => onBookTap(book.id),
          onLongPress: () => onLongPress(book.id),
        );
      },
    );
  }
}

class _BookItem extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final bool isEditMode;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _BookItem({
    required this.book,
    this.serverUrl,
    this.authToken,
    required this.isEditMode,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
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
    final displayRating = book.userRating ?? book.rating ?? book.averageRating;

    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
        decoration: BoxDecoration(
          color: sapphoSurfaceLight,
          borderRadius: BorderRadius.circular(8),
          border: isSelected ? Border.all(color: sapphoInfo, width: 2) : null,
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

                  // Selection checkbox (top-left in edit mode)
                  if (isEditMode)
                    Positioned(
                      top: 4,
                      left: 4,
                      child: Container(
                        width: 20,
                        height: 20,
                        decoration: BoxDecoration(
                          color: isSelected
                              ? sapphoInfo
                              : Colors.black.withValues(alpha: 0.5),
                          shape: BoxShape.circle,
                          border: isSelected
                              ? null
                              : Border.all(
                                  color: Colors.white.withValues(alpha: 0.5),
                                  width: 2,
                                ),
                        ),
                        child: isSelected
                            ? const Icon(
                                Icons.check,
                                color: Colors.white,
                                size: 12,
                              )
                            : null,
                      ),
                    ),

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
                  // Title
                  Text(
                    book.title,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
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
