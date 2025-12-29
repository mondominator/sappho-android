import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:file_picker/file_picker.dart';
import '../../models/audiobook.dart';
import '../../providers/auth_provider.dart';
import '../../providers/library_provider.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Library view modes
enum LibraryView {
  categories,
  series,
  seriesBooks,
  authors,
  authorBooks,
  genres,
  genreBooks,
  allBooks,
}

/// Library screen matching Android LibraryScreen.kt
class LibraryScreen extends StatefulWidget {
  final Function(int)? onAudiobookTap;
  final VoidCallback? onCollectionsTap;
  final VoidCallback? onReadingListTap;
  final VoidCallback? onBackToProfile;
  final bool showBackToProfile;

  const LibraryScreen({
    super.key,
    this.onAudiobookTap,
    this.onCollectionsTap,
    this.onReadingListTap,
    this.onBackToProfile,
    this.showBackToProfile = false,
  });

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  LibraryView _currentView = LibraryView.categories;
  String? _selectedSeries;
  String? _selectedAuthor;
  String? _selectedGenre;
  bool _hasRestoredState = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Restore state from provider on first build
    if (!_hasRestoredState) {
      _hasRestoredState = true;
      final library = context.read<LibraryProvider>();
      final viewIndex = library.currentViewIndex;

      // For detail views (seriesBooks, authorBooks, genreBooks), fall back to
      // the parent list view since we don't preserve the selection
      if (viewIndex == LibraryView.seriesBooks.index) {
        _currentView = LibraryView.series;
      } else if (viewIndex == LibraryView.authorBooks.index) {
        _currentView = LibraryView.authors;
      } else if (viewIndex == LibraryView.genreBooks.index) {
        _currentView = LibraryView.genres;
      } else if (viewIndex < LibraryView.values.length) {
        _currentView = LibraryView.values[viewIndex];
      }
    }
  }

  void _resetToCategories() {
    setState(() {
      _currentView = LibraryView.categories;
      _selectedSeries = null;
      _selectedAuthor = null;
      _selectedGenre = null;
    });
    _saveStateToProvider();
  }

  void _saveStateToProvider() {
    final library = context.read<LibraryProvider>();
    // Only save view index - don't save selections to avoid interfering
    // with external navigation (from detail screen)
    library.setViewIndex(_currentView.index);
  }

  void _navigateTo(
    LibraryView view, {
    String? series,
    String? author,
    String? genre,
  }) {
    setState(() {
      _currentView = view;
      // Always set all selections - clear those not explicitly provided
      _selectedSeries = series;
      _selectedAuthor = author;
      _selectedGenre = genre;
    });
    _saveStateToProvider();
  }

  void _checkProviderSelection(LibraryProvider library) {
    // Check if the provider has a pending selection (e.g., from detail page link click)
    if (library.selectedAuthor != null) {
      _selectedAuthor = library.selectedAuthor;
      _currentView = LibraryView.authorBooks;
      library.clearSelection();
      _saveStateToProvider();
    } else if (library.selectedSeries != null) {
      _selectedSeries = library.selectedSeries;
      _currentView = LibraryView.seriesBooks;
      library.clearSelection();
      _saveStateToProvider();
    } else if (library.selectedGenre != null) {
      _selectedGenre = library.selectedGenre;
      _currentView = LibraryView.genreBooks;
      library.clearSelection();
      _saveStateToProvider();
    }
  }

  void _goBack() {
    setState(() {
      switch (_currentView) {
        case LibraryView.series:
        case LibraryView.authors:
        case LibraryView.genres:
        case LibraryView.allBooks:
          _currentView = LibraryView.categories;
          // Clear all selections when going back to categories
          _selectedSeries = null;
          _selectedAuthor = null;
          _selectedGenre = null;
          break;
        case LibraryView.seriesBooks:
          _currentView = LibraryView.series;
          _selectedSeries = null; // Clear series selection
          break;
        case LibraryView.authorBooks:
          _currentView = LibraryView.authors;
          _selectedAuthor = null; // Clear author selection
          break;
        case LibraryView.genreBooks:
          _currentView = LibraryView.genres;
          _selectedGenre = null; // Clear genre selection
          break;
        case LibraryView.categories:
          break;
      }
    });
    _saveStateToProvider();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _currentView == LibraryView.categories,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          _goBack();
        }
      },
      child: Consumer<LibraryProvider>(
        builder: (context, library, child) {
          // Check if provider was reset to categories (e.g., by tapping Library tab)
          if (library.currentViewIndex == 0 &&
              _currentView != LibraryView.categories) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              _resetToCategories();
            });
          }
          // Check if we should navigate based on provider selection
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (library.selectedAuthor != null ||
                library.selectedSeries != null ||
                library.selectedGenre != null) {
              setState(() {
                _checkProviderSelection(library);
              });
            }
          });

          if (library.isLoading && library.allAudiobooks.isEmpty) {
            return const Center(
              child: CircularProgressIndicator(color: sapphoInfo),
            );
          }

          final auth = context.watch<AuthProvider>();

          return switch (_currentView) {
            LibraryView.categories => _CategoriesView(
              totalBooks: library.allAudiobooks.length,
              seriesCount: library.series.length,
              authorsCount: library.authors.length,
              genresCount: library.genres.length,
              readingListCount: library.readingList.length,
              isAdmin: auth.user?.isAdminUser ?? false,
              collectionsCount: library.collectionsCount,
              onSeriesClick: () => _navigateTo(LibraryView.series),
              onAuthorsClick: () => _navigateTo(LibraryView.authors),
              onGenresClick: () => _navigateTo(LibraryView.genres),
              onAllBooksClick: () => _navigateTo(LibraryView.allBooks),
              onReadingListClick: widget.onReadingListTap ?? () {},
              onCollectionsClick: widget.onCollectionsTap,
              showBackToProfile: widget.showBackToProfile,
              onBackToProfile: widget.onBackToProfile,
            ),
            LibraryView.series => _SeriesListView(
              series: library.series,
              allBooks: library.allAudiobooks,
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onSeriesClick: (name) =>
                  _navigateTo(LibraryView.seriesBooks, series: name),
            ),
            LibraryView.seriesBooks => _SeriesBooksView(
              seriesName: _selectedSeries ?? '',
              books: library.getBooksForSeries(_selectedSeries ?? ''),
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onBookClick: widget.onAudiobookTap,
            ),
            LibraryView.authors => _AuthorsListView(
              authors: library.authors,
              allBooks: library.allAudiobooks,
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onAuthorClick: (name) =>
                  _navigateTo(LibraryView.authorBooks, author: name),
            ),
            LibraryView.authorBooks => _AuthorBooksView(
              authorName: _selectedAuthor ?? '',
              books: library.getBooksForAuthor(_selectedAuthor ?? ''),
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onBookClick: widget.onAudiobookTap,
            ),
            LibraryView.genres => _GenresListView(
              genres: library.genres,
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onGenreClick: (name) =>
                  _navigateTo(LibraryView.genreBooks, genre: name),
            ),
            LibraryView.genreBooks => _GenreBooksView(
              genreName: _selectedGenre ?? '',
              books: library.getBooksForGenre(_selectedGenre ?? ''),
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onBookClick: widget.onAudiobookTap,
            ),
            LibraryView.allBooks => _AllBooksView(
              books: library.allAudiobooks,
              serverUrl: library.serverUrl,
              authToken: library.authToken,
              onBackClick: _goBack,
              onBookClick: widget.onAudiobookTap,
            ),
          };
        },
      ),
    );
  }
}

/// Categories view - main library landing page
class _CategoriesView extends StatelessWidget {
  final int totalBooks;
  final int seriesCount;
  final int authorsCount;
  final int genresCount;
  final int readingListCount;
  final int collectionsCount;
  final bool isAdmin;
  final VoidCallback onSeriesClick;
  final VoidCallback onAuthorsClick;
  final VoidCallback onGenresClick;
  final VoidCallback onAllBooksClick;
  final VoidCallback onReadingListClick;
  final VoidCallback? onCollectionsClick;

  final VoidCallback? onBackToProfile;
  final bool showBackToProfile;

  const _CategoriesView({
    required this.totalBooks,
    required this.seriesCount,
    required this.authorsCount,
    required this.genresCount,
    required this.readingListCount,
    required this.collectionsCount,
    required this.isAdmin,
    required this.onSeriesClick,
    required this.onAuthorsClick,
    required this.onGenresClick,
    required this.onAllBooksClick,
    required this.onReadingListClick,
    this.onCollectionsClick,
    this.onBackToProfile,
    this.showBackToProfile = false,
  });

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;
    return ListView(
      padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 16),
      children: [
        const SizedBox(height: 8),

        // Header
        Row(
          children: [
            if (showBackToProfile) ...[
              IconButton(
                onPressed: onBackToProfile,
                icon: const Icon(Icons.arrow_back, color: Colors.white),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
              const SizedBox(width: 12),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Library',
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: sapphoText,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '$totalBooks audiobooks in your collection',
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

        const SizedBox(height: 16),

        // Series - Large card
        _CategoryCardLarge(
          icon: Icons.menu_book,
          title: 'Series',
          count: seriesCount,
          gradientColors: categoryContentColors,
          onTap: onSeriesClick,
        ),

        const SizedBox(height: 12),

        // Authors & Genres row
        Row(
          children: [
            Expanded(
              child: _CategoryCardMedium(
                icon: Icons.person,
                title: 'Authors',
                count: authorsCount,
                gradientColors: categoryContentColors,
                onTap: onAuthorsClick,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _CategoryCardMedium(
                icon: Icons.category,
                title: 'Genres',
                count: genresCount,
                gradientColors: categoryContentColors,
                onTap: onGenresClick,
              ),
            ),
          ],
        ),

        const SizedBox(height: 12),

        // Reading List & Collections row
        Row(
          children: [
            Expanded(
              child: _CategoryCardMedium(
                icon: Icons.bookmark_added,
                title: 'Reading List',
                count: readingListCount,
                gradientColors: categoryPersonalColors,
                onTap: onReadingListClick,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _CategoryCardMedium(
                icon: Icons.folder,
                title: 'Collections',
                count: collectionsCount,
                gradientColors: [sapphoInfo, sapphoInfo.withValues(alpha: 0.7)],
                onTap: onCollectionsClick ?? () {},
              ),
            ),
          ],
        ),

        const SizedBox(height: 12),

        // All Books - Wide card
        _CategoryCardWide(
          icon: Icons.grid_view,
          title: 'All Books',
          gradientColors: categoryNeutralColors,
          onTap: onAllBooksClick,
        ),

        // Upload button for admin users
        if (isAdmin) ...[
          const SizedBox(height: 16),
          _CategoryCardWide(
            icon: Icons.upload,
            title: 'Upload Audiobook',
            gradientColors: const [sapphoSuccess, Color(0xFF166534)],
            onTap: () => _showUploadDialog(context),
          ),
        ],

        const SizedBox(height: 16),
      ],
    );
  }

  void _showUploadDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: sapphoSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => const _UploadAudiobookSheet(),
    );
  }
}

/// Upload audiobook sheet
class _UploadAudiobookSheet extends StatefulWidget {
  const _UploadAudiobookSheet();

  @override
  State<_UploadAudiobookSheet> createState() => _UploadAudiobookSheetState();
}

class _UploadAudiobookSheetState extends State<_UploadAudiobookSheet> {
  File? _selectedFile;
  String? _fileName;
  bool _isUploading = false;
  double _uploadProgress = 0;
  String? _error;

  final _titleController = TextEditingController();
  final _authorController = TextEditingController();
  final _narratorController = TextEditingController();
  final _seriesController = TextEditingController();
  final _seriesPositionController = TextEditingController();

  @override
  void dispose() {
    _titleController.dispose();
    _authorController.dispose();
    _narratorController.dispose();
    _seriesController.dispose();
    _seriesPositionController.dispose();
    super.dispose();
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['mp3', 'm4a', 'm4b', 'mp4', 'ogg', 'flac'],
      );

      if (result != null && result.files.single.path != null) {
        setState(() {
          _selectedFile = File(result.files.single.path!);
          _fileName = result.files.single.name;
          _error = null;

          // Pre-fill title from filename (remove extension)
          if (_titleController.text.isEmpty) {
            final nameWithoutExt = _fileName!.replaceAll(
              RegExp(r'\.[^.]+$'),
              '',
            );
            _titleController.text = nameWithoutExt;
          }
        });
      }
    } catch (e) {
      setState(() => _error = 'Failed to pick file: $e');
    }
  }

  Future<void> _upload() async {
    if (_selectedFile == null) {
      setState(() => _error = 'Please select a file first');
      return;
    }

    setState(() {
      _isUploading = true;
      _uploadProgress = 0;
      _error = null;
    });

    try {
      final api = context.read<ApiService>();
      await api.uploadAudiobook(
        _selectedFile!,
        title: _titleController.text.isNotEmpty ? _titleController.text : null,
        author: _authorController.text.isNotEmpty
            ? _authorController.text
            : null,
        narrator: _narratorController.text.isNotEmpty
            ? _narratorController.text
            : null,
        series: _seriesController.text.isNotEmpty
            ? _seriesController.text
            : null,
        seriesPosition: _seriesPositionController.text.isNotEmpty
            ? double.tryParse(_seriesPositionController.text)
            : null,
        onProgress: (sent, total) {
          setState(() => _uploadProgress = sent / total);
        },
      );

      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Audiobook uploaded successfully'),
            backgroundColor: sapphoSuccess,
          ),
        );
        // Refresh library
        context.read<LibraryProvider>().refresh();
      }
    } catch (e) {
      setState(() {
        _isUploading = false;
        _error = 'Upload failed: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: DraggableScrollableSheet(
        initialChildSize: 0.8,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        expand: false,
        builder: (_, scrollController) => Column(
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
                  const Text(
                    'Upload Audiobook',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: sapphoTextMuted),
                  ),
                ],
              ),
            ),
            const Divider(color: sapphoSurfaceBorder, height: 1),
            // Content
            Expanded(
              child: ListView(
                controller: scrollController,
                padding: const EdgeInsets.all(16),
                children: [
                  // File selector
                  GestureDetector(
                    onTap: _isUploading ? null : _pickFile,
                    child: Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: sapphoSurfaceLight,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: _selectedFile != null
                              ? sapphoSuccess
                              : sapphoSurfaceBorder,
                          width: _selectedFile != null ? 2 : 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          Icon(
                            _selectedFile != null
                                ? Icons.audio_file
                                : Icons.upload_file,
                            size: 48,
                            color: _selectedFile != null
                                ? sapphoSuccess
                                : sapphoTextMuted,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            _selectedFile != null
                                ? _fileName!
                                : 'Tap to select audio file',
                            style: TextStyle(
                              color: _selectedFile != null
                                  ? Colors.white
                                  : sapphoTextMuted,
                              fontSize: 14,
                            ),
                            textAlign: TextAlign.center,
                          ),
                          if (_selectedFile == null) ...[
                            const SizedBox(height: 4),
                            const Text(
                              'MP3, M4A, M4B, MP4, OGG, FLAC',
                              style: TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Metadata fields (optional)
                  const Text(
                    'Metadata (optional)',
                    style: TextStyle(
                      color: sapphoTextMuted,
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 12),

                  _UploadTextField(
                    controller: _titleController,
                    label: 'Title',
                    icon: Icons.title,
                  ),
                  const SizedBox(height: 12),
                  _UploadTextField(
                    controller: _authorController,
                    label: 'Author',
                    icon: Icons.person,
                  ),
                  const SizedBox(height: 12),
                  _UploadTextField(
                    controller: _narratorController,
                    label: 'Narrator',
                    icon: Icons.mic,
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        flex: 2,
                        child: _UploadTextField(
                          controller: _seriesController,
                          label: 'Series',
                          icon: Icons.library_books,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _UploadTextField(
                          controller: _seriesPositionController,
                          label: '#',
                          icon: Icons.tag,
                          keyboardType: TextInputType.number,
                        ),
                      ),
                    ],
                  ),

                  // Error message
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: sapphoError.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.error_outline,
                            color: sapphoError,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _error!,
                              style: const TextStyle(
                                color: sapphoError,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],

                  // Upload progress
                  if (_isUploading) ...[
                    const SizedBox(height: 16),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Uploading...',
                              style: TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 13,
                              ),
                            ),
                            Text(
                              '${(_uploadProgress * 100).toInt()}%',
                              style: const TextStyle(
                                color: sapphoInfo,
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: _uploadProgress,
                            backgroundColor: sapphoProgressTrack,
                            valueColor: const AlwaysStoppedAnimation(
                              sapphoInfo,
                            ),
                            minHeight: 8,
                          ),
                        ),
                      ],
                    ),
                  ],

                  const SizedBox(height: 24),

                  // Upload button
                  ElevatedButton(
                    onPressed: _isUploading || _selectedFile == null
                        ? null
                        : _upload,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: sapphoSuccess,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      disabledBackgroundColor: sapphoSurfaceLight,
                    ),
                    child: _isUploading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          )
                        : const Text(
                            'Upload',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
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

class _UploadTextField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final IconData icon;
  final TextInputType? keyboardType;

  const _UploadTextField({
    required this.controller,
    required this.label,
    required this.icon,
    this.keyboardType,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: sapphoBackground,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: sapphoSurfaceBorder),
      ),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        style: const TextStyle(color: Colors.white, fontSize: 14),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: sapphoTextMuted, fontSize: 13),
          prefixIcon: Icon(icon, color: sapphoTextMuted, size: 18),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 14,
            vertical: 14,
          ),
        ),
      ),
    );
  }
}

/// Large category card (for Series)
class _CategoryCardLarge extends StatelessWidget {
  final IconData icon;
  final String title;
  final int count;
  final String? label;
  final List<Color> gradientColors;
  final VoidCallback onTap;

  const _CategoryCardLarge({
    required this.icon,
    required this.title,
    required this.count,
    this.label,
    required this.gradientColors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 140,
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: gradientColors),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(20),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '$count',
                        style: const TextStyle(
                          fontSize: 36,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                      if (label != null) ...[
                        const SizedBox(width: 8),
                        Padding(
                          padding: const EdgeInsets.only(bottom: 6),
                          child: Text(
                            label!,
                            style: TextStyle(
                              fontSize: 16,
                              color: Colors.white.withValues(alpha: 0.8),
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 40, color: Colors.white),
            ),
          ],
        ),
      ),
    );
  }
}

/// Medium category card
class _CategoryCardMedium extends StatelessWidget {
  final IconData icon;
  final String title;
  final int count;
  final List<Color> gradientColors;
  final VoidCallback onTap;

  const _CategoryCardMedium({
    required this.icon,
    required this.title,
    required this.count,
    required this.gradientColors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 160,
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: gradientColors),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, size: 28, color: Colors.white),
            ),
            const Spacer(),
            Text(
              '$count',
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            Text(
              title,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: Colors.white.withValues(alpha: 0.9),
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}

/// Wide category card (for All Books)
class _CategoryCardWide extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final List<Color> gradientColors;
  final VoidCallback onTap;

  const _CategoryCardWide({
    required this.icon,
    required this.title,
    this.subtitle,
    required this.gradientColors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 80,
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: gradientColors),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, size: 28, color: Colors.white),
                ),
                const SizedBox(width: 16),
                Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                    if (subtitle != null)
                      Text(
                        subtitle!,
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.white.withValues(alpha: 0.7),
                        ),
                      ),
                  ],
                ),
              ],
            ),
            Icon(
              Icons.chevron_right,
              size: 24,
              color: Colors.white.withValues(alpha: 0.5),
            ),
          ],
        ),
      ),
    );
  }
}

/// Series list view
class _SeriesListView extends StatelessWidget {
  final List<SeriesInfo> series;
  final List<Audiobook> allBooks;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(String) onSeriesClick;

  const _SeriesListView({
    required this.series,
    required this.allBooks,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    required this.onSeriesClick,
  });

  // Gradient colors matching genres (blue-focused palette)
  static const List<List<Color>> _seriesGradients = [
    [Color(0xFF4A5568), Color(0xFF2D3748)], // Slate gray
    [Color(0xFF285E61), Color(0xFF1A3A3C)], // Dark teal
    [Color(0xFF553C9A), Color(0xFF322659)], // Deep purple
    [Color(0xFF1E40AF), Color(0xFF1E3A8A)], // Indigo blue
    [Color(0xFF2B6CB0), Color(0xFF1A365D)], // Navy blue
    [Color(0xFF276749), Color(0xFF1C4532)], // Forest green
    [Color(0xFF0E7490), Color(0xFF164E63)], // Cyan blue
    [Color(0xFF4338CA), Color(0xFF312E81)], // Deep indigo
    [Color(0xFF2C5282), Color(0xFF1A365D)], // Steel blue
    [Color(0xFF0D9488), Color(0xFF115E59)], // Teal
  ];

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Column(
      children: [
        // Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              IconButton(
                onPressed: onBackClick,
                icon: const Icon(Icons.arrow_back, color: Colors.white),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Series',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    '${series.length} series in your library',
                    style: const TextStyle(
                      fontSize: 13,
                      color: sapphoIconDefault,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        // Series list
        Expanded(
          child: ListView.separated(
            padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 100),
            itemCount: series.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final seriesItem = series[index];
              final seriesBooks =
                  allBooks
                      .where((book) => book.series == seriesItem.series)
                      .toList()
                    ..sort(
                      (a, b) => (a.seriesPosition ?? 0).compareTo(
                        b.seriesPosition ?? 0,
                      ),
                    );
              final totalDuration = seriesBooks.fold<int>(
                0,
                (sum, book) => sum + (book.duration ?? 0),
              );
              final coverIds = seriesBooks.take(5).map((b) => b.id).toList();
              final gradientColors =
                  _seriesGradients[seriesItem.series.hashCode.abs() %
                      _seriesGradients.length];

              return _SeriesCard(
                seriesName: seriesItem.series,
                bookCount: seriesItem.bookCount,
                totalDuration: totalDuration,
                gradientColors: gradientColors,
                coverIds: coverIds,
                serverUrl: serverUrl,
                authToken: authToken,
                onTap: () => onSeriesClick(seriesItem.series),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Series card - matching genre card style
class _SeriesCard extends StatelessWidget {
  final String seriesName;
  final int bookCount;
  final int totalDuration;
  final List<Color> gradientColors;
  final List<int> coverIds;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onTap;

  const _SeriesCard({
    required this.seriesName,
    required this.bookCount,
    required this.totalDuration,
    required this.gradientColors,
    required this.coverIds,
    required this.serverUrl,
    required this.authToken,
    required this.onTap,
  });

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    return '${hours}h ${minutes}m';
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: gradientColors,
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top row: Icon, title, stats, chevron
            Row(
              children: [
                // Icon
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.collections_bookmark,
                    color: Colors.white,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                // Series info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        seriesName,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        '$bookCount ${bookCount == 1 ? 'book' : 'books'}${totalDuration > 0 ? ' • ${_formatDuration(totalDuration)}' : ''}',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Colors.white.withValues(alpha: 0.9),
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.chevron_right,
                  color: Colors.white.withValues(alpha: 0.7),
                  size: 24,
                ),
              ],
            ),

            // Book covers
            if (coverIds.isNotEmpty && serverUrl != null) ...[
              const SizedBox(height: 12),
              Row(
                children: coverIds.map((bookId) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Container(
                      width: 50,
                      height: 50,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.3),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.2),
                          width: 1,
                        ),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: CachedNetworkImage(
                        imageUrl: '$serverUrl/api/audiobooks/$bookId/cover',
                        fit: BoxFit.cover,
                        memCacheWidth: 100,
                        memCacheHeight: 100,
                        fadeInDuration: Duration.zero,
                        fadeOutDuration: Duration.zero,
                        httpHeaders: authToken != null
                            ? {'Authorization': 'Bearer $authToken'}
                            : null,
                        errorWidget: (_, __, ___) => const Icon(
                          Icons.book,
                          color: Colors.white54,
                          size: 24,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Series books view with Catch Me Up feature
class _SeriesBooksView extends StatefulWidget {
  final String seriesName;
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(int)? onBookClick;

  const _SeriesBooksView({
    required this.seriesName,
    required this.books,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    this.onBookClick,
  });

  @override
  State<_SeriesBooksView> createState() => _SeriesBooksViewState();
}

class _SeriesBooksViewState extends State<_SeriesBooksView> {
  bool _aiConfigured = false;
  bool _isLoadingRecap = false;
  bool _showRecap = false;
  String? _recapData;
  String? _recapError;
  List<dynamic>? _booksIncluded;

  @override
  void initState() {
    super.initState();
    _checkAiStatus();
  }

  Future<void> _checkAiStatus() async {
    try {
      final api = context.read<ApiService>();
      final status = await api.getAiStatus();
      if (mounted) {
        setState(() => _aiConfigured = status.configured);
      }
    } catch (e) {
      debugPrint('SeriesView: Failed to check AI status: $e');
    }
  }

  bool get _hasProgress {
    return widget.books.any(
      (book) =>
          (book.progress?.position ?? 0) > 0 || book.progress?.completed == 1,
    );
  }

  Future<void> _loadRecap() async {
    setState(() {
      _isLoadingRecap = true;
      _recapError = null;
      _showRecap = true;
    });

    try {
      final api = context.read<ApiService>();
      final recap = await api.getSeriesRecap(widget.seriesName);
      if (mounted) {
        setState(() {
          _recapData = recap.recap;
          _booksIncluded = recap.booksIncluded;
          _isLoadingRecap = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _recapError = e.toString();
          _isLoadingRecap = false;
        });
      }
    }
  }

  Future<void> _regenerateRecap() async {
    try {
      final api = context.read<ApiService>();
      await api.clearSeriesRecap(widget.seriesName);
    } catch (e) {
      debugPrint('SeriesView: Failed to clear recap: $e');
    }
    await _loadRecap();
  }

  @override
  Widget build(BuildContext context) {
    final totalDuration = widget.books.fold<int>(
      0,
      (sum, book) => sum + (book.duration ?? 0),
    );
    final bookAuthors = widget.books
        .map((b) => b.author)
        .whereType<String>()
        .toSet()
        .toList();
    final showCatchMeUp = _aiConfigured && _hasProgress;
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: ListView(
        padding: EdgeInsets.only(bottom: bottomPadding + 100),
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            child: Row(
              children: [
                IconButton(
                  onPressed: widget.onBackClick,
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.seriesName,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Row(
                        children: [
                          Text(
                            '${widget.books.length} ${widget.books.length == 1 ? 'book' : 'books'}',
                            style: const TextStyle(
                              fontSize: 13,
                              color: sapphoIconDefault,
                            ),
                          ),
                          if (totalDuration > 0) ...[
                            const SizedBox(width: 8),
                            const Text(
                              '•',
                              style: TextStyle(color: sapphoIconDefault),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '${totalDuration ~/ 3600}h ${(totalDuration % 3600) ~/ 60}m',
                              style: const TextStyle(
                                fontSize: 13,
                                color: sapphoIconDefault,
                              ),
                            ),
                          ],
                          // Catch Me Up button
                          if (showCatchMeUp &&
                              !_showRecap &&
                              !_isLoadingRecap &&
                              _recapData == null) ...[
                            const Spacer(),
                            GestureDetector(
                              onTap: _loadRecap,
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: sapphoFeatureAccent,
                                  borderRadius: BorderRadius.circular(16),
                                ),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(
                                      Icons.auto_awesome,
                                      size: 12,
                                      color: Colors.white,
                                    ),
                                    SizedBox(width: 4),
                                    Text(
                                      'Recap',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      if (bookAuthors.isNotEmpty)
                        Text(
                          bookAuthors.join(', '),
                          style: const TextStyle(
                            fontSize: 13,
                            color: sapphoInfo,
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

          // Catch Me Up expanded content (loading, error, or recap)
          if (_showRecap ||
              _isLoadingRecap ||
              _recapError != null ||
              _recapData != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: _buildCatchMeUpContent(),
            ),

          // Books grid
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                mainAxisSpacing: 16,
                crossAxisSpacing: 16,
                childAspectRatio: 0.85,
              ),
              itemCount: widget.books.length,
              itemBuilder: (context, index) {
                final book = widget.books[index];
                return _SeriesBookTile(
                  book: book,
                  serverUrl: widget.serverUrl,
                  authToken: widget.authToken,
                  onTap: () => widget.onBookClick?.call(book.id),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCatchMeUpContent() {
    // Loading state
    if (_isLoadingRecap) {
      return Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: sapphoFeatureAccent.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                color: sapphoAccentLight,
                strokeWidth: 2,
              ),
            ),
            const SizedBox(width: 12),
            Text(
              'Generating your personalized recap...',
              style: TextStyle(color: sapphoAccentLight, fontSize: 14),
            ),
          ],
        ),
      );
    }

    // Error state
    if (_recapError != null) {
      return Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: sapphoError.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            const Icon(Icons.warning, color: sapphoErrorLight, size: 20),
            const SizedBox(height: 8),
            Text(
              _recapError!,
              style: const TextStyle(color: sapphoErrorLight, fontSize: 14),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            TextButton(onPressed: _loadRecap, child: const Text('Try Again')),
          ],
        ),
      );
    }

    // Recap content
    if (_recapData != null) {
      return Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: sapphoFeatureAccent.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: sapphoFeatureAccent.withValues(alpha: 0.2)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.auto_awesome, color: sapphoFeatureAccent, size: 20),
                const SizedBox(width: 8),
                const Text(
                  'Catch Me Up',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: sapphoFeatureAccent,
                  ),
                ),
                const Spacer(),
                IconButton(
                  onPressed: () => setState(() {
                    _showRecap = false;
                    _recapData = null;
                    _booksIncluded = null;
                  }),
                  icon: const Icon(
                    Icons.close,
                    size: 18,
                    color: sapphoIconDefault,
                  ),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            if (_booksIncluded != null && _booksIncluded!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'Based on: ${_booksIncluded!.map((b) => b.title).join(', ')}',
                style: const TextStyle(fontSize: 12, color: sapphoTextMuted),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
            const SizedBox(height: 12),
            Text(
              _recapData!,
              style: const TextStyle(
                fontSize: 14,
                color: sapphoTextLight,
                height: 1.5,
              ),
            ),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: _regenerateRecap,
                icon: const Icon(Icons.refresh, size: 16),
                label: const Text('Regenerate'),
                style: TextButton.styleFrom(foregroundColor: sapphoIconDefault),
              ),
            ),
          ],
        ),
      );
    }

    return const SizedBox.shrink();
  }
}

/// Series book tile for grid view
class _SeriesBookTile extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback? onTap;

  const _SeriesBookTile({
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
                      fit: BoxFit.contain,
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

                  // Reading list ribbon (top-right)
                  if (book.isFavorite)
                    Positioned(
                      top: 0,
                      right: 8,
                      child: CustomPaint(
                        size: const Size(14, 24),
                        painter: _ReadingListRibbonPainter(),
                      ),
                    ),
                ],
              ),
            ),

            // Book info
            Padding(
              padding: const EdgeInsets.all(8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Title - marquee for long titles
                  SizedBox(
                    height: 18,
                    width: double.infinity,
                    child: _MarqueeText(
                      text: book.title,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  // Book number, duration, and rating row
                  Row(
                    children: [
                      // Series position
                      if (book.seriesPosition != null) ...[
                        Text(
                          '#${book.formattedSeriesPosition}',
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: sapphoInfo,
                          ),
                        ),
                        const Text(
                          ' • ',
                          style: TextStyle(
                            fontSize: 11,
                            color: sapphoIconDefault,
                          ),
                        ),
                      ],
                      // Duration
                      if (book.duration != null)
                        Text(
                          '${book.duration! ~/ 3600}h ${(book.duration! % 3600) ~/ 60}m',
                          style: const TextStyle(
                            fontSize: 11,
                            color: sapphoIconDefault,
                          ),
                        ),
                      // Rating
                      if (displayRating != null && displayRating > 0) ...[
                        if (book.duration != null)
                          const Text(
                            ' • ',
                            style: TextStyle(
                              fontSize: 11,
                              color: sapphoIconDefault,
                            ),
                          ),
                        const Icon(
                          Icons.star,
                          size: 11,
                          color: sapphoStarFilled,
                        ),
                        const SizedBox(width: 2),
                        Text(
                          displayRating.toStringAsFixed(1),
                          style: const TextStyle(
                            fontSize: 11,
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

/// Authors list view
class _AuthorsListView extends StatelessWidget {
  final List<AuthorInfo> authors;
  final List<Audiobook> allBooks;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(String) onAuthorClick;

  const _AuthorsListView({
    required this.authors,
    required this.allBooks,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    required this.onAuthorClick,
  });

  // Gradient colors matching genres (blue-focused palette)
  static const List<List<Color>> _authorGradients = [
    [Color(0xFF4A5568), Color(0xFF2D3748)], // Slate gray
    [Color(0xFF285E61), Color(0xFF1A3A3C)], // Dark teal
    [Color(0xFF553C9A), Color(0xFF322659)], // Deep purple
    [Color(0xFF1E40AF), Color(0xFF1E3A8A)], // Indigo blue
    [Color(0xFF2B6CB0), Color(0xFF1A365D)], // Navy blue
    [Color(0xFF276749), Color(0xFF1C4532)], // Forest green
    [Color(0xFF0E7490), Color(0xFF164E63)], // Cyan blue
    [Color(0xFF4338CA), Color(0xFF312E81)], // Deep indigo
    [Color(0xFF2C5282), Color(0xFF1A365D)], // Steel blue
    [Color(0xFF0D9488), Color(0xFF115E59)], // Teal
  ];

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Column(
      children: [
        // Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              IconButton(
                onPressed: onBackClick,
                icon: const Icon(Icons.arrow_back, color: Colors.white),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Authors',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    '${authors.length} authors in your library',
                    style: const TextStyle(
                      fontSize: 13,
                      color: sapphoIconDefault,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        // Authors list
        Expanded(
          child: ListView.separated(
            padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 100),
            itemCount: authors.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final author = authors[index];
              // Get books by this author
              final authorBooks = allBooks
                  .where((book) => book.author == author.author)
                  .toList();
              final totalDuration = authorBooks.fold<int>(
                0,
                (sum, book) => sum + (book.duration ?? 0),
              );
              final coverIds = authorBooks.take(5).map((b) => b.id).toList();
              final gradientColors =
                  _authorGradients[author.author.hashCode.abs() %
                      _authorGradients.length];

              return _AuthorCard(
                authorName: author.author,
                bookCount: author.bookCount,
                totalDuration: totalDuration,
                gradientColors: gradientColors,
                coverIds: coverIds,
                serverUrl: serverUrl,
                authToken: authToken,
                onTap: () => onAuthorClick(author.author),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Author card - matching genre card style
class _AuthorCard extends StatelessWidget {
  final String authorName;
  final int bookCount;
  final int totalDuration;
  final List<Color> gradientColors;
  final List<int> coverIds;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onTap;

  const _AuthorCard({
    required this.authorName,
    required this.bookCount,
    required this.totalDuration,
    required this.gradientColors,
    required this.coverIds,
    required this.serverUrl,
    required this.authToken,
    required this.onTap,
  });

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    return '${hours}h ${minutes}m';
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: gradientColors,
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top row: Icon, title, stats, chevron
            Row(
              children: [
                // Icon
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.person,
                    color: Colors.white,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                // Author info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        authorName,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        '$bookCount ${bookCount == 1 ? 'book' : 'books'}${totalDuration > 0 ? ' • ${_formatDuration(totalDuration)}' : ''}',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Colors.white.withValues(alpha: 0.9),
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.chevron_right,
                  color: Colors.white.withValues(alpha: 0.7),
                  size: 24,
                ),
              ],
            ),

            // Book covers
            if (coverIds.isNotEmpty && serverUrl != null) ...[
              const SizedBox(height: 12),
              Row(
                children: coverIds.map((bookId) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Container(
                      width: 50,
                      height: 50,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.3),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.2),
                          width: 1,
                        ),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: CachedNetworkImage(
                        imageUrl: '$serverUrl/api/audiobooks/$bookId/cover',
                        fit: BoxFit.cover,
                        memCacheWidth: 100,
                        memCacheHeight: 100,
                        fadeInDuration: Duration.zero,
                        fadeOutDuration: Duration.zero,
                        httpHeaders: authToken != null
                            ? {'Authorization': 'Bearer $authToken'}
                            : null,
                        errorWidget: (_, __, ___) => const Icon(
                          Icons.book,
                          color: Colors.white54,
                          size: 24,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Author books view - matches Android AuthorBooksView
class _AuthorBooksView extends StatelessWidget {
  final String authorName;
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(int)? onBookClick;

  const _AuthorBooksView({
    required this.authorName,
    required this.books,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    this.onBookClick,
  });

  // Avatar gradient colors (matching Android)
  static const List<List<Color>> _avatarColors = [
    [Color(0xFF667EEA), Color(0xFF764BA2)], // Purple-blue
    [Color(0xFF11998E), Color(0xFF38EF7D)], // Teal-green
    [Color(0xFFFC466B), Color(0xFF3F5EFB)], // Pink-blue
    [Color(0xFFF093FB), Color(0xFFF5576C)], // Pink-coral
    [Color(0xFF4FACFE), Color(0xFF00F2FE)], // Blue-cyan
    [Color(0xFF43E97B), Color(0xFF38F9D7)], // Green-teal
    [Color(0xFFFA709A), Color(0xFFFEE140)], // Pink-yellow
    [Color(0xFF30CFD0), Color(0xFF330867)], // Cyan-purple
  ];

  @override
  Widget build(BuildContext context) {
    final totalDuration = books.fold<int>(
      0,
      (sum, book) => sum + (book.duration ?? 0),
    );
    final seriesList = books
        .map((b) => b.series)
        .whereType<String>()
        .toSet()
        .toList();
    final completedBooks = books
        .where((b) => b.progress?.completed == 1)
        .length;

    // Group books by series
    final booksBySeries = <String, List<Audiobook>>{};
    final standaloneBooks = <Audiobook>[];
    for (final book in books) {
      if (book.series != null && book.series!.isNotEmpty) {
        booksBySeries.putIfAbsent(book.series!, () => []).add(book);
      } else {
        standaloneBooks.add(book);
      }
    }
    // Sort each series by position
    for (final series in booksBySeries.keys) {
      booksBySeries[series]!.sort(
        (a, b) => (a.seriesPosition ?? 0).compareTo(b.seriesPosition ?? 0),
      );
    }
    // Sort series by name
    final sortedSeriesNames = booksBySeries.keys.toList()..sort();
    // Sort standalone books by title
    standaloneBooks.sort((a, b) => a.title.compareTo(b.title));

    // Generate initials and color from author name
    final nameParts = authorName.split(' ');
    final initials = nameParts
        .take(2)
        .map((p) => p.isNotEmpty ? p[0].toUpperCase() : '')
        .join();
    final colorIndex = authorName.hashCode.abs() % _avatarColors.length;
    final avatarGradient = _avatarColors[colorIndex];

    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: ListView(
        padding: EdgeInsets.only(bottom: bottomPadding + 100),
        children: [
          // Hero Section with gradient background
          Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  avatarGradient[0].withValues(alpha: 0.4),
                  sapphoBackground,
                ],
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Back button
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.3),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      onPressed: onBackClick,
                      icon: const Icon(Icons.arrow_back, color: Colors.white),
                    ),
                  ),
                ),

                // Avatar and name row
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Row(
                    children: [
                      // Avatar
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(colors: avatarGradient),
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.2),
                            width: 3,
                          ),
                        ),
                        child: Center(
                          child: Text(
                            initials,
                            style: const TextStyle(
                              fontSize: 28,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      // Name
                      Expanded(
                        child: Text(
                          authorName,
                          style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Stats row
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Row(
              children: [
                Expanded(
                  child: _AuthorStatCard(
                    value: '${books.length}',
                    label: books.length == 1 ? 'Book' : 'Books',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _AuthorStatCard(
                    value: '${seriesList.length}',
                    label: 'Series',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _AuthorStatCard(
                    value: '${totalDuration ~/ 3600}h',
                    label: 'Total Time',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _AuthorStatCard(
                    value: '$completedBooks',
                    label: 'Finished',
                  ),
                ),
              ],
            ),
          ),

          // Series sections
          ...sortedSeriesNames.map(
            (seriesName) =>
                _buildSeriesSection(seriesName, booksBySeries[seriesName]!),
          ),

          // Standalone books section
          if (standaloneBooks.isNotEmpty)
            _buildStandaloneSection(standaloneBooks),
        ],
      ),
    );
  }

  Widget _buildSeriesSection(String seriesName, List<Audiobook> seriesBooks) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section header
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: Text(
                        seriesName,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '(${seriesBooks.length})',
                      style: const TextStyle(
                        fontSize: 14,
                        color: sapphoIconDefault,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: sapphoInfo.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  'Series',
                  style: TextStyle(fontSize: 11, color: sapphoInfo),
                ),
              ),
            ],
          ),
        ),
        // Horizontal book row with series-style tiles
        SizedBox(
          height: 160,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: seriesBooks.length,
            separatorBuilder: (_, __) => const SizedBox(width: 10),
            itemBuilder: (context, index) {
              final book = seriesBooks[index];
              return SizedBox(
                width: 110,
                child: _SeriesBookTile(
                  book: book,
                  serverUrl: serverUrl,
                  authToken: authToken,
                  onTap: () => onBookClick?.call(book.id),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildStandaloneSection(List<Audiobook> standaloneBooks) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section header
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 28, 16, 12),
          child: Row(
            children: [
              const Text(
                'Standalone Books',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '(${standaloneBooks.length})',
                style: const TextStyle(fontSize: 16, color: sapphoIconDefault),
              ),
            ],
          ),
        ),
        // Grid for standalone books
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: 16,
              crossAxisSpacing: 16,
              childAspectRatio: 0.85,
            ),
            itemCount: standaloneBooks.length,
            itemBuilder: (context, index) {
              final book = standaloneBooks[index];
              return _SeriesBookTile(
                book: book,
                serverUrl: serverUrl,
                authToken: authToken,
                onTap: () => onBookClick?.call(book.id),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Author stat card
class _AuthorStatCard extends StatelessWidget {
  final String value;
  final String label;

  const _AuthorStatCard({required this.value, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 10, color: sapphoIconDefault),
            maxLines: 1,
          ),
        ],
      ),
    );
  }
}

/// Genres list view
class _GenresListView extends StatelessWidget {
  final List<GenreInfo> genres;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(String) onGenreClick;

  const _GenresListView({
    required this.genres,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    required this.onGenreClick,
  });

  // Gradient colors for genres (blue-focused palette)
  static const List<List<Color>> _genreGradients = [
    [Color(0xFF4A5568), Color(0xFF2D3748)], // Slate gray
    [Color(0xFF285E61), Color(0xFF1A3A3C)], // Dark teal
    [Color(0xFF553C9A), Color(0xFF322659)], // Deep purple
    [Color(0xFF1E40AF), Color(0xFF1E3A8A)], // Indigo blue
    [Color(0xFF2B6CB0), Color(0xFF1A365D)], // Navy blue
    [Color(0xFF276749), Color(0xFF1C4532)], // Forest green
    [Color(0xFF0E7490), Color(0xFF164E63)], // Cyan blue
    [Color(0xFF4338CA), Color(0xFF312E81)], // Deep indigo
    [Color(0xFF2C5282), Color(0xFF1A365D)], // Steel blue
    [Color(0xFF0D9488), Color(0xFF115E59)], // Teal
  ];

  // Icons for genres based on common names
  IconData _getGenreIcon(String genre) {
    final lower = genre.toLowerCase();
    if (lower.contains('fiction')) return Icons.auto_stories;
    if (lower.contains('mystery') || lower.contains('thriller'))
      return Icons.search;
    if (lower.contains('romance')) return Icons.favorite;
    if (lower.contains('science') || lower.contains('sci-fi'))
      return Icons.rocket_launch;
    if (lower.contains('fantasy')) return Icons.auto_fix_high;
    if (lower.contains('horror')) return Icons.nights_stay;
    if (lower.contains('history') || lower.contains('historical'))
      return Icons.history_edu;
    if (lower.contains('biography') || lower.contains('memoir'))
      return Icons.person;
    if (lower.contains('business') || lower.contains('finance'))
      return Icons.trending_up;
    if (lower.contains('self-help') || lower.contains('self help'))
      return Icons.psychology;
    if (lower.contains('health') || lower.contains('wellness'))
      return Icons.favorite_border;
    if (lower.contains('travel')) return Icons.flight;
    if (lower.contains('cook') || lower.contains('food'))
      return Icons.restaurant;
    if (lower.contains('child') || lower.contains('kids'))
      return Icons.child_care;
    if (lower.contains('young adult') || lower.contains('ya'))
      return Icons.school;
    if (lower.contains('comedy') || lower.contains('humor'))
      return Icons.sentiment_very_satisfied;
    if (lower.contains('drama')) return Icons.theater_comedy;
    if (lower.contains('action') || lower.contains('adventure'))
      return Icons.explore;
    if (lower.contains('crime')) return Icons.gavel;
    if (lower.contains('non-fiction') || lower.contains('nonfiction'))
      return Icons.menu_book;
    return Icons.library_books;
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Column(
      children: [
        // Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              IconButton(
                onPressed: onBackClick,
                icon: const Icon(Icons.arrow_back, color: Colors.white),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Genres',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    '${genres.length} genres in your library',
                    style: const TextStyle(
                      fontSize: 13,
                      color: sapphoIconDefault,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        // Genres list
        Expanded(
          child: ListView.separated(
            padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 100),
            itemCount: genres.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final genre = genres[index];
              final gradientColors =
                  _genreGradients[genre.genre.hashCode.abs() %
                      _genreGradients.length];
              final icon = _getGenreIcon(genre.genre);

              return _GenreCard(
                genreName: genre.genre,
                bookCount: genre.count,
                totalDuration: genre.totalDuration,
                gradientColors: gradientColors,
                icon: icon,
                coverIds: genre.coverIds,
                serverUrl: serverUrl,
                authToken: authToken,
                onTap: () => onGenreClick(genre.genre),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Genre card - modern design matching Android
class _GenreCard extends StatelessWidget {
  final String genreName;
  final int bookCount;
  final int totalDuration;
  final List<Color> gradientColors;
  final IconData icon;
  final List<int> coverIds;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onTap;

  const _GenreCard({
    required this.genreName,
    required this.bookCount,
    required this.totalDuration,
    required this.gradientColors,
    required this.icon,
    required this.coverIds,
    required this.serverUrl,
    required this.authToken,
    required this.onTap,
  });

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    return '${hours}h ${minutes}m';
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: gradientColors,
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top row: Icon, title, stats, chevron
            Row(
              children: [
                // Icon
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: Colors.white, size: 22),
                ),
                const SizedBox(width: 12),
                // Genre info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        genreName,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        '$bookCount ${bookCount == 1 ? 'book' : 'books'}${totalDuration > 0 ? ' • ${_formatDuration(totalDuration)}' : ''}',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Colors.white.withValues(alpha: 0.9),
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.chevron_right,
                  color: Colors.white.withValues(alpha: 0.7),
                  size: 24,
                ),
              ],
            ),

            // Book covers from server-provided IDs
            if (coverIds.isNotEmpty && serverUrl != null) ...[
              const SizedBox(height: 12),
              Row(
                children: coverIds.map((bookId) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Container(
                      width: 50,
                      height: 50,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.3),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.2),
                          width: 1,
                        ),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: CachedNetworkImage(
                        imageUrl: '$serverUrl/api/audiobooks/$bookId/cover',
                        fit: BoxFit.cover,
                        memCacheWidth: 100,
                        memCacheHeight: 100,
                        fadeInDuration: Duration.zero,
                        fadeOutDuration: Duration.zero,
                        httpHeaders: authToken != null
                            ? {'Authorization': 'Bearer $authToken'}
                            : null,
                        errorWidget: (_, __, ___) => const Icon(
                          Icons.book,
                          color: Colors.white54,
                          size: 24,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Genre books view
class _GenreBooksView extends StatelessWidget {
  final String genreName;
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(int)? onBookClick;

  const _GenreBooksView({
    required this.genreName,
    required this.books,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    this.onBookClick,
  });

  @override
  Widget build(BuildContext context) {
    final totalDuration = books.fold<int>(
      0,
      (sum, book) => sum + (book.duration ?? 0),
    );
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    // Group books by series
    final booksBySeries = <String, List<Audiobook>>{};
    final standaloneBooks = <Audiobook>[];
    for (final book in books) {
      if (book.series != null && book.series!.isNotEmpty) {
        booksBySeries.putIfAbsent(book.series!, () => []).add(book);
      } else {
        standaloneBooks.add(book);
      }
    }
    // Sort each series by position
    for (final series in booksBySeries.keys) {
      booksBySeries[series]!.sort(
        (a, b) => (a.seriesPosition ?? 0).compareTo(b.seriesPosition ?? 0),
      );
    }
    // Sort series by name
    final sortedSeriesNames = booksBySeries.keys.toList()..sort();
    // Sort standalone books by title
    standaloneBooks.sort((a, b) => a.title.compareTo(b.title));

    return Container(
      color: sapphoBackground,
      child: ListView(
        padding: EdgeInsets.only(bottom: bottomPadding + 100),
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            child: Row(
              children: [
                IconButton(
                  onPressed: onBackClick,
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        genreName,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Row(
                        children: [
                          Text(
                            '${books.length} ${books.length == 1 ? 'book' : 'books'}',
                            style: const TextStyle(
                              fontSize: 13,
                              color: sapphoIconDefault,
                            ),
                          ),
                          if (totalDuration > 0) ...[
                            const SizedBox(width: 8),
                            const Text(
                              '•',
                              style: TextStyle(color: sapphoIconDefault),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '${totalDuration ~/ 3600}h ${(totalDuration % 3600) ~/ 60}m',
                              style: const TextStyle(
                                fontSize: 13,
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

          // Series sections first
          ...sortedSeriesNames.map(
            (seriesName) =>
                _buildSeriesSection(seriesName, booksBySeries[seriesName]!),
          ),

          // Standalone books section
          if (standaloneBooks.isNotEmpty)
            _buildStandaloneSection(standaloneBooks),
        ],
      ),
    );
  }

  Widget _buildSeriesSection(String seriesName, List<Audiobook> seriesBooks) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section header
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: Text(
                        seriesName,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '(${seriesBooks.length})',
                      style: const TextStyle(
                        fontSize: 14,
                        color: sapphoIconDefault,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: sapphoInfo.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  'Series',
                  style: TextStyle(fontSize: 11, color: sapphoInfo),
                ),
              ),
            ],
          ),
        ),
        // Horizontal book row
        SizedBox(
          height: 160,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: seriesBooks.length,
            separatorBuilder: (_, __) => const SizedBox(width: 10),
            itemBuilder: (context, index) {
              final book = seriesBooks[index];
              return SizedBox(
                width: 110,
                child: _SeriesBookTile(
                  book: book,
                  serverUrl: serverUrl,
                  authToken: authToken,
                  onTap: () => onBookClick?.call(book.id),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildStandaloneSection(List<Audiobook> standaloneBooks) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section header
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 28, 16, 12),
          child: Row(
            children: [
              const Text(
                'Standalone Books',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '(${standaloneBooks.length})',
                style: const TextStyle(fontSize: 16, color: sapphoIconDefault),
              ),
            ],
          ),
        ),
        // Grid for standalone books
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: 16,
              crossAxisSpacing: 16,
              childAspectRatio: 0.85,
            ),
            itemCount: standaloneBooks.length,
            itemBuilder: (context, index) {
              final book = standaloneBooks[index];
              return _SeriesBookTile(
                book: book,
                serverUrl: serverUrl,
                authToken: authToken,
                onTap: () => onBookClick?.call(book.id),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// All books view
class _AllBooksView extends StatefulWidget {
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback onBackClick;
  final Function(int)? onBookClick;

  const _AllBooksView({
    required this.books,
    required this.serverUrl,
    required this.authToken,
    required this.onBackClick,
    this.onBookClick,
  });

  @override
  State<_AllBooksView> createState() => _AllBooksViewState();
}

class _AllBooksViewState extends State<_AllBooksView> {
  String _sortBy = 'title';
  bool _ascending = true;
  String _filterBy = 'all';

  List<Audiobook> get _filteredBooks {
    return widget.books.where((book) {
      final hasProgress = (book.progress?.position ?? 0) > 0;
      final isFinished = book.progress?.completed == 1;

      switch (_filterBy) {
        case 'hide_finished':
          return !isFinished;
        case 'in_progress':
          return hasProgress && !isFinished;
        case 'not_started':
          return !hasProgress && !isFinished;
        case 'finished':
          return isFinished;
        case 'all':
        default:
          return true;
      }
    }).toList();
  }

  List<Audiobook> get _sortedBooks {
    final sorted = List<Audiobook>.from(_filteredBooks);
    sorted.sort((a, b) {
      int result;
      switch (_sortBy) {
        case 'title':
          result = a.title.toLowerCase().compareTo(b.title.toLowerCase());
          break;
        case 'author':
          result = (a.author?.toLowerCase() ?? '').compareTo(
            b.author?.toLowerCase() ?? '',
          );
          break;
        case 'recent':
          result = (b.createdAt ?? '').compareTo(a.createdAt ?? '');
          break;
        case 'duration':
          result = (a.duration ?? 0).compareTo(b.duration ?? 0);
          break;
        case 'progress':
          final aProgress = a.duration != null && a.duration! > 0
              ? (a.progress?.position ?? 0) / a.duration!
              : 0.0;
          final bProgress = b.duration != null && b.duration! > 0
              ? (b.progress?.position ?? 0) / b.duration!
              : 0.0;
          result = bProgress.compareTo(aProgress);
          break;
        case 'rating':
          final aRating = a.userRating ?? a.averageRating ?? 0;
          final bRating = b.userRating ?? b.averageRating ?? 0;
          result = bRating.compareTo(aRating);
          break;
        case 'series':
          final aSeries = a.series ?? '\uFFFF';
          final bSeries = b.series ?? '\uFFFF';
          result = aSeries.compareTo(bSeries);
          if (result == 0) {
            result = (a.seriesPosition ?? 0).compareTo(b.seriesPosition ?? 0);
          }
          break;
        default:
          result = a.title.compareTo(b.title);
      }
      // For naturally descending sorts, flip the logic
      final naturallyDescending = [
        'recent',
        'progress',
        'rating',
      ].contains(_sortBy);
      if (naturallyDescending) {
        return _ascending ? -result : result;
      }
      return _ascending ? result : -result;
    });
    return sorted;
  }

  String _getFilterLabel(String filter) {
    switch (filter) {
      case 'all':
        return 'All Books';
      case 'hide_finished':
        return 'Hide Finished';
      case 'in_progress':
        return 'In Progress';
      case 'not_started':
        return 'Not Started';
      case 'finished':
        return 'Finished';
      default:
        return 'All Books';
    }
  }

  @override
  Widget build(BuildContext context) {
    final filteredCount = _filteredBooks.length;
    final totalCount = widget.books.length;
    final countText = filteredCount == totalCount
        ? '$totalCount audiobooks'
        : '$filteredCount of $totalCount audiobooks';

    return Column(
      children: [
        // Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              IconButton(
                onPressed: widget.onBackClick,
                icon: const Icon(Icons.arrow_back, color: Colors.white),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'All Books',
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    Text(
                      countText,
                      style: const TextStyle(
                        fontSize: 13,
                        color: sapphoIconDefault,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Filter and Sort row
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              // Show filter
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Show',
                      style: TextStyle(fontSize: 12, color: sapphoIconDefault),
                    ),
                    const SizedBox(height: 4),
                    PopupMenuButton<String>(
                      color: sapphoSurface,
                      onSelected: (value) {
                        setState(() => _filterBy = value);
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        decoration: BoxDecoration(
                          color: sapphoSurfaceDark,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _getFilterLabel(_filterBy),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 14,
                              ),
                            ),
                            const Icon(
                              Icons.arrow_drop_down,
                              color: Colors.white,
                              size: 20,
                            ),
                          ],
                        ),
                      ),
                      itemBuilder: (context) => [
                        _buildFilterMenuItem('all', 'All Books'),
                        _buildFilterMenuItem('hide_finished', 'Hide Finished'),
                        _buildFilterMenuItem('in_progress', 'In Progress'),
                        _buildFilterMenuItem('not_started', 'Not Started'),
                        _buildFilterMenuItem('finished', 'Finished'),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              // Sort dropdown
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Sort by',
                      style: TextStyle(fontSize: 12, color: sapphoIconDefault),
                    ),
                    const SizedBox(height: 4),
                    PopupMenuButton<String>(
                      color: sapphoSurface,
                      onSelected: (value) {
                        setState(() {
                          if (_sortBy == value) {
                            _ascending = !_ascending;
                          } else {
                            _sortBy = value;
                            _ascending = true;
                          }
                        });
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        decoration: BoxDecoration(
                          color: sapphoSurfaceDark,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _getSortLabel(_sortBy),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 14,
                              ),
                            ),
                            Icon(
                              _ascending
                                  ? Icons.arrow_upward
                                  : Icons.arrow_downward,
                              color: Colors.white,
                              size: 16,
                            ),
                          ],
                        ),
                      ),
                      itemBuilder: (context) => [
                        _buildSortMenuItem('title', 'Title'),
                        _buildSortMenuItem('author', 'Author'),
                        _buildSortMenuItem('recent', 'Recently Added'),
                        _buildSortMenuItem('duration', 'Duration'),
                        _buildSortMenuItem('progress', 'Progress'),
                        _buildSortMenuItem('rating', 'Rating'),
                        _buildSortMenuItem('series', 'Series'),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Books grid
        Expanded(
          child: _BooksGrid(
            books: _sortedBooks,
            serverUrl: widget.serverUrl,
            authToken: widget.authToken,
            onBookClick: widget.onBookClick,
          ),
        ),
      ],
    );
  }

  String _getSortLabel(String sort) {
    switch (sort) {
      case 'title':
        return 'Title';
      case 'author':
        return 'Author';
      case 'recent':
        return 'Recently Added';
      case 'duration':
        return 'Duration';
      case 'progress':
        return 'Progress';
      case 'rating':
        return 'Rating';
      case 'series':
        return 'Series';
      default:
        return 'Title';
    }
  }

  PopupMenuItem<String> _buildFilterMenuItem(String value, String label) {
    final isSelected = _filterBy == value;
    return PopupMenuItem(
      value: value,
      child: Row(
        children: [
          Text(
            label,
            style: TextStyle(
              color: isSelected ? sapphoInfo : sapphoText,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
          if (isSelected) ...[
            const Spacer(),
            const Icon(Icons.check, size: 16, color: sapphoInfo),
          ],
        ],
      ),
    );
  }

  PopupMenuItem<String> _buildSortMenuItem(String value, String label) {
    final isSelected = _sortBy == value;
    return PopupMenuItem(
      value: value,
      child: Row(
        children: [
          Text(
            label,
            style: TextStyle(
              color: isSelected ? sapphoInfo : sapphoText,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
          if (isSelected) ...[
            const Spacer(),
            Icon(
              _ascending ? Icons.arrow_upward : Icons.arrow_downward,
              size: 16,
              color: sapphoInfo,
            ),
          ],
        ],
      ),
    );
  }
}

/// Books grid widget
class _BooksGrid extends StatelessWidget {
  final List<Audiobook> books;
  final String? serverUrl;
  final String? authToken;
  final Function(int)? onBookClick;
  final bool showSeriesPosition;

  const _BooksGrid({
    required this.books,
    required this.serverUrl,
    required this.authToken,
    this.onBookClick,
    this.showSeriesPosition = false,
  });

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        MediaQuery.of(context).padding.bottom + 16,
      ),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.0,
      ),
      itemCount: books.length,
      itemBuilder: (context, index) {
        final book = books[index];
        return LibraryGridItem(
          book: book,
          serverUrl: serverUrl,
          authToken: authToken,
          showSeriesPosition: showSeriesPosition,
          onTap: () => onBookClick?.call(book.id),
        );
      },
    );
  }
}

/// Library grid item - square cover with overlays (no title/author text)
/// Matches Android BookGridItem
class LibraryGridItem extends StatelessWidget {
  final Audiobook book;
  final String? serverUrl;
  final String? authToken;
  final bool showSeriesPosition;
  final VoidCallback? onTap;

  const LibraryGridItem({
    super.key,
    required this.book,
    required this.serverUrl,
    this.authToken,
    this.showSeriesPosition = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AspectRatio(
        aspectRatio: 1.0,
        child: Container(
          decoration: BoxDecoration(
            color: sapphoProgressTrack,
            borderRadius: BorderRadius.circular(8),
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Cover image
              if (book.coverImage != null && serverUrl != null)
                CachedNetworkImage(
                  imageUrl: '$serverUrl/api/audiobooks/${book.id}/cover',
                  fit: BoxFit.cover,
                  memCacheWidth: 200, // Cache at reasonable size
                  memCacheHeight: 200,
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

              // Series position badge (top-left)
              if (showSeriesPosition && book.seriesPosition != null)
                Positioned(
                  top: 6,
                  left: 6,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: sapphoInfo,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      '#${book.formattedSeriesPosition}',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),

              // Reading list ribbon (top-right)
              if (book.isFavorite)
                Positioned(
                  top: 0,
                  right: 8,
                  child: CustomPaint(
                    size: const Size(18, 32),
                    painter: _ReadingListRibbonPainter(),
                  ),
                ),

              // Progress bar at bottom
              if (book.progress != null &&
                  (book.progress!.position > 0 ||
                      book.progress!.completed == 1) &&
                  book.duration != null)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: _buildProgressBar(),
                ),
            ],
          ),
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

  Widget _buildProgressBar() {
    final progress = book.progress!;
    final progressPercent = progress.completed == 1
        ? 1.0
        : (progress.position / book.duration!).clamp(0.0, 1.0);

    return Container(
      height: 5,
      color: Colors.black.withValues(alpha: 0.7),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: progressPercent,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: progress.completed == 1
                  ? [sapphoSuccess, const Color(0xFF22C55E)]
                  : [sapphoInfo, const Color(0xFF60A5FA)],
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

/// Marquee text widget for long titles
class _MarqueeText extends StatefulWidget {
  final String text;
  final TextStyle style;

  const _MarqueeText({required this.text, required this.style});

  @override
  State<_MarqueeText> createState() => _MarqueeTextState();
}

class _MarqueeTextState extends State<_MarqueeText>
    with SingleTickerProviderStateMixin {
  late ScrollController _scrollController;
  Timer? _scrollTimer;
  Timer? _pauseTimer;
  bool _isScrolling = false;
  double _maxScrollExtent = 0;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    // Use a small delay to ensure layout is complete for complex parent layouts
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
    _scrollTimer?.cancel();
    _pauseTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _checkOverflow() {
    if (!mounted) return;
    if (_scrollController.hasClients) {
      _maxScrollExtent = _scrollController.position.maxScrollExtent;
      if (_maxScrollExtent > 0 && !_isScrolling) {
        _startScrolling();
      }
    }
  }

  void _startScrolling() {
    _isScrolling = true;
    _pauseTimer = Timer(const Duration(seconds: 2), () {
      _scrollForward();
    });
  }

  void _scrollForward() {
    if (!mounted || !_scrollController.hasClients) return;
    _scrollController
        .animateTo(
          _maxScrollExtent,
          duration: Duration(milliseconds: (_maxScrollExtent * 30).toInt()),
          curve: Curves.linear,
        )
        .then((_) {
          if (!mounted) return;
          _pauseTimer = Timer(const Duration(seconds: 2), () {
            _scrollBack();
          });
        });
  }

  void _scrollBack() {
    if (!mounted || !_scrollController.hasClients) return;
    _scrollController
        .animateTo(
          0,
          duration: Duration(milliseconds: (_maxScrollExtent * 30).toInt()),
          curve: Curves.linear,
        )
        .then((_) {
          if (!mounted) return;
          _pauseTimer = Timer(const Duration(seconds: 2), () {
            _scrollForward();
          });
        });
  }

  void _stopScrolling() {
    _scrollTimer?.cancel();
    _pauseTimer?.cancel();
    _isScrolling = false;
    if (_scrollController.hasClients) {
      _scrollController.jumpTo(0);
    }
  }

  @override
  Widget build(BuildContext context) {
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
