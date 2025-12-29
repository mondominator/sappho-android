import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../models/collection.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Collections screen matching Android CollectionsScreen.kt
/// User-created collections of audiobooks
class CollectionsScreen extends StatefulWidget {
  final Function(int)? onCollectionTap;
  final VoidCallback? onBack;

  const CollectionsScreen({super.key, this.onCollectionTap, this.onBack});

  @override
  State<CollectionsScreen> createState() => _CollectionsScreenState();
}

class _CollectionsScreenState extends State<CollectionsScreen> {
  List<Collection> _collections = [];
  bool _isLoading = false;
  bool _isEditMode = false;
  Set<int> _selectedCollections = {};
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadCollections();
    });
  }

  Future<void> _loadCollections() async {
    if (!mounted) return;
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final api = context.read<ApiService>();
      final collections = await api.getCollections();
      setState(() {
        _collections = collections;
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
      _selectedCollections = {};
    });
  }

  void _handleBack() {
    if (_isEditMode) {
      _exitEditMode();
    } else {
      widget.onBack?.call();
    }
  }

  Future<void> _deleteSelectedCollections() async {
    if (_selectedCollections.isEmpty) return;

    final count = _selectedCollections.length;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Delete Collections',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          count == 1
              ? 'Are you sure you want to delete this collection?'
              : 'Are you sure you want to delete $count collections?',
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
            child: const Text('Delete', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      final api = context.read<ApiService>();
      for (final collectionId in _selectedCollections) {
        try {
          await api.deleteCollection(collectionId);
        } catch (e) {
          debugPrint('Failed to delete collection $collectionId: $e');
        }
      }
      if (mounted) {
        _exitEditMode();
        _loadCollections();
      }
    }
  }

  void _showCreateDialog() {
    final nameController = TextEditingController();
    final descController = TextEditingController();
    bool isCreating = false;
    final api = context.read<ApiService>();

    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          backgroundColor: sapphoSurface,
          title: const Text(
            'Create Collection',
            style: TextStyle(color: Colors.white),
          ),
          content: Column(
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
            ],
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
              onPressed: isCreating
                  ? null
                  : () async {
                      if (nameController.text.isEmpty) return;
                      setDialogState(() => isCreating = true);
                      try {
                        await api.createCollection(
                          nameController.text,
                          description: descController.text.isEmpty
                              ? null
                              : descController.text,
                        );
                        if (mounted) {
                          Navigator.pop(dialogContext);
                          _loadCollections();
                        }
                      } catch (e) {
                        debugPrint('Failed to create collection: $e');
                      }
                    },
              style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              child: isCreating
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Text('Create'),
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
                  child: Text(
                    _isEditMode
                        ? '${_selectedCollections.length} selected'
                        : 'Collections',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                if (_isEditMode && _selectedCollections.isNotEmpty)
                  IconButton(
                    onPressed: _deleteSelectedCollections,
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
                    onPressed: _showCreateDialog,
                    icon: Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        color: sapphoInfo,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.add,
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
                          onPressed: _loadCollections,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: sapphoInfo,
                          ),
                          child: const Text('Retry'),
                        ),
                      ],
                    ),
                  )
                : _collections.isEmpty
                ? _EmptyState(onCreateTap: _showCreateDialog)
                : _CollectionsGrid(
                    collections: _collections,
                    isEditMode: _isEditMode,
                    selectedCollections: _selectedCollections,
                    onCollectionTap: (id) {
                      if (_isEditMode) {
                        setState(() {
                          if (_selectedCollections.contains(id)) {
                            _selectedCollections.remove(id);
                          } else {
                            _selectedCollections.add(id);
                          }
                        });
                      } else {
                        widget.onCollectionTap?.call(id);
                      }
                    },
                    onLongPress: (id) {
                      setState(() {
                        _isEditMode = true;
                        _selectedCollections = {id};
                      });
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
  final VoidCallback? onCreateTap;

  const _EmptyState({this.onCreateTap});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.library_books, size: 64, color: sapphoInfo),
            const SizedBox(height: 16),
            const Text(
              'No collections yet',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Create a collection to organize your audiobooks',
              style: TextStyle(fontSize: 14, color: sapphoIconDefault),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: onCreateTap,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Create Collection'),
              style: ElevatedButton.styleFrom(
                backgroundColor: sapphoInfo,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CollectionsGrid extends StatelessWidget {
  final List<Collection> collections;
  final bool isEditMode;
  final Set<int> selectedCollections;
  final Function(int) onCollectionTap;
  final Function(int) onLongPress;
  final double bottomPadding;

  const _CollectionsGrid({
    required this.collections,
    required this.isEditMode,
    required this.selectedCollections,
    required this.onCollectionTap,
    required this.onLongPress,
    required this.bottomPadding,
  });

  @override
  Widget build(BuildContext context) {
    final serverUrl = context.watch<AuthProvider>().serverUrl;

    return GridView.builder(
      padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 16,
        crossAxisSpacing: 16,
        childAspectRatio: 0.75,
      ),
      itemCount: collections.length,
      itemBuilder: (context, index) {
        final collection = collections[index];
        final isSelected = selectedCollections.contains(collection.id);
        final isPublic = collection.isPublic == 1;

        return GestureDetector(
          onTap: () => onCollectionTap(collection.id),
          onLongPress: () => onLongPress(collection.id),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: isSelected
                  ? Border.all(color: sapphoInfo, width: 2)
                  : null,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.3),
                  blurRadius: 8,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            clipBehavior: Clip.antiAlias,
            child: Stack(
              fit: StackFit.expand,
              children: [
                // Full bleed rotating cover
                _RotatingCover(
                  bookIds: collection.bookIds,
                  serverUrl: serverUrl,
                  collectionName: collection.name,
                ),

                // Gradient overlay for text readability
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: 100,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.transparent,
                          Colors.black.withValues(alpha: 0.8),
                        ],
                      ),
                    ),
                  ),
                ),

                // Public/Private badge (top-left)
                Positioned(
                  top: 8,
                  left: 8,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: isPublic
                          ? sapphoSuccess.withValues(alpha: 0.9)
                          : sapphoProgressTrack.withValues(alpha: 0.9),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          isPublic ? Icons.public : Icons.lock,
                          size: 12,
                          color: Colors.white,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          isPublic ? 'Public' : 'Private',
                          style: const TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // Info overlay (bottom)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          collection.name,
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            Text(
                              '${collection.bookCount} ${collection.bookCount == 1 ? 'book' : 'books'}',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.white.withValues(alpha: 0.8),
                              ),
                            ),
                            if (collection.creatorUsername != null) ...[
                              Text(
                                ' • ',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.white.withValues(alpha: 0.6),
                                ),
                              ),
                              Expanded(
                                child: Text(
                                  collection.creatorUsername!,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.white.withValues(alpha: 0.7),
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ],
                    ),
                  ),
                ),

                // Edit mode selection checkbox
                if (isEditMode)
                  Positioned(
                    top: 8,
                    right: 8,
                    child: Container(
                      width: 24,
                      height: 24,
                      decoration: BoxDecoration(
                        color: isSelected
                            ? sapphoInfo
                            : Colors.black.withValues(alpha: 0.5),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSelected
                              ? sapphoInfo
                              : Colors.white.withValues(alpha: 0.5),
                          width: 2,
                        ),
                      ),
                      child: isSelected
                          ? const Icon(
                              Icons.check,
                              color: Colors.white,
                              size: 14,
                            )
                          : null,
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// Rotating cover widget that cycles through book covers every 4 seconds
class _RotatingCover extends StatefulWidget {
  final List<int>? bookIds;
  final String? serverUrl;
  final String collectionName;

  const _RotatingCover({
    required this.bookIds,
    required this.serverUrl,
    required this.collectionName,
  });

  @override
  State<_RotatingCover> createState() => _RotatingCoverState();
}

class _RotatingCoverState extends State<_RotatingCover> {
  int _currentIndex = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _startRotation();
  }

  @override
  void didUpdateWidget(_RotatingCover oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.bookIds != widget.bookIds) {
      _currentIndex = 0;
      _startRotation();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startRotation() {
    _timer?.cancel();
    final bookIds = widget.bookIds;
    if (bookIds != null && bookIds.length > 1) {
      _timer = Timer.periodic(const Duration(seconds: 4), (_) {
        if (mounted) {
          setState(() {
            _currentIndex = (_currentIndex + 1) % bookIds.length;
          });
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bookIds = widget.bookIds;
    final serverUrl = widget.serverUrl;

    // If no books, show placeholder
    if (bookIds == null || bookIds.isEmpty || serverUrl == null) {
      return Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [sapphoProgressTrack, sapphoSurfaceDark],
          ),
        ),
        child: const Center(
          child: Icon(Icons.library_books, color: sapphoInfo, size: 48),
        ),
      );
    }

    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 500),
      child: Container(
        key: ValueKey(bookIds[_currentIndex]),
        decoration: BoxDecoration(color: sapphoSurfaceDark),
        child: CachedNetworkImage(
          imageUrl: '$serverUrl/api/audiobooks/${bookIds[_currentIndex]}/cover',
          fit: BoxFit.cover,
          width: double.infinity,
          height: double.infinity,
          memCacheWidth: 400,
          memCacheHeight: 400,
          placeholder: (context, url) => Container(
            color: sapphoSurfaceDark,
            child: const Center(
              child: CircularProgressIndicator(
                color: sapphoInfo,
                strokeWidth: 2,
              ),
            ),
          ),
          errorWidget: (context, url, error) => Container(
            color: sapphoSurfaceDark,
            child: const Center(
              child: Icon(Icons.library_books, color: sapphoInfo, size: 48),
            ),
          ),
        ),
      ),
    );
  }
}
