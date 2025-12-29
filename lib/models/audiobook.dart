/// Audiobook model matching Android's Audiobook.kt
class Audiobook {
  final int id;
  final String title;
  final String? subtitle;
  final String? author;
  final String? narrator;
  final String? series;
  final double? seriesPosition;
  final String? description;
  final String? genre;
  final String? normalizedGenre;
  final String? tags;
  final int? publishYear;
  final int? copyrightYear;
  final String? publisher;
  final String? isbn;
  final String? asin;
  final String? language;
  final double? rating;
  final double? userRating;
  final double? averageRating;
  final int? abridged; // 0 = unabridged, 1 = abridged
  final String? coverImage;
  final int fileCount;
  final int? isMultiFile;
  final String? createdAt;
  final int? duration;
  final Progress? progress;
  final List<Chapter>? chapters;
  final bool isFavorite;

  Audiobook({
    required this.id,
    required this.title,
    this.subtitle,
    this.author,
    this.narrator,
    this.series,
    this.seriesPosition,
    this.description,
    this.genre,
    this.normalizedGenre,
    this.tags,
    this.publishYear,
    this.copyrightYear,
    this.publisher,
    this.isbn,
    this.asin,
    this.language,
    this.rating,
    this.userRating,
    this.averageRating,
    this.abridged,
    this.coverImage,
    this.fileCount = 1,
    this.isMultiFile,
    this.createdAt,
    this.duration,
    this.progress,
    this.chapters,
    this.isFavorite = false,
  });

  factory Audiobook.fromJson(Map<String, dynamic> json) {
    return Audiobook(
      id: _parseInt(json['id']) ?? 0,
      title: json['title']?.toString() ?? 'Unknown',
      subtitle: json['subtitle']?.toString(),
      author: json['author']?.toString(),
      narrator: json['narrator']?.toString(),
      series: json['series']?.toString(),
      seriesPosition: _parseDouble(json['series_position']),
      description: json['description']?.toString(),
      genre: json['genre']?.toString(),
      normalizedGenre: json['normalized_genre']?.toString(),
      tags: json['tags']?.toString(),
      publishYear: _parseInt(json['published_year']),
      copyrightYear: _parseInt(json['copyright_year']),
      publisher: json['publisher']?.toString(),
      isbn: json['isbn']?.toString(),
      asin: json['asin']?.toString(),
      language: json['language']?.toString(),
      rating: _parseDouble(json['rating']),
      userRating: _parseDouble(json['user_rating']),
      averageRating: _parseDouble(json['average_rating']),
      abridged: _parseInt(json['abridged']),
      coverImage: json['cover_image']?.toString(),
      fileCount: _parseInt(json['file_count']) ?? 1,
      isMultiFile: _parseInt(json['is_multi_file']),
      createdAt: json['created_at']?.toString(),
      duration: _parseInt(json['duration']),
      progress: json['progress'] != null
          ? Progress.fromJson(json['progress'])
          : (json['progress_position'] != null ||
                json['progress_completed'] != null)
          ? Progress(
              position: _parseInt(json['progress_position']) ?? 0,
              completed: _parseInt(json['progress_completed']) ?? 0,
            )
          : null,
      chapters: json['chapters'] != null
          ? (json['chapters'] as List).map((c) => Chapter.fromJson(c)).toList()
          : null,
      isFavorite: json['is_favorite'] == true || json['is_favorite'] == 1,
    );
  }

  /// Safely parse an int from a value that might be a string, number, or null
  static int? _parseInt(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value);
    return null;
  }

  /// Safely parse a double from a value that might be a string, number, or null
  static double? _parseDouble(dynamic value) {
    if (value == null) return null;
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'subtitle': subtitle,
      'author': author,
      'narrator': narrator,
      'series': series,
      'series_position': seriesPosition,
      'description': description,
      'genre': genre,
      'normalized_genre': normalizedGenre,
      'tags': tags,
      'published_year': publishYear,
      'copyright_year': copyrightYear,
      'publisher': publisher,
      'isbn': isbn,
      'asin': asin,
      'language': language,
      'rating': rating,
      'user_rating': userRating,
      'average_rating': averageRating,
      'abridged': abridged,
      'cover_image': coverImage,
      'file_count': fileCount,
      'is_multi_file': isMultiFile,
      'created_at': createdAt,
      'duration': duration,
      'progress': progress?.toJson(),
      'chapters': chapters?.map((c) => c.toJson()).toList(),
      'is_favorite': isFavorite,
    };
  }

  Audiobook copyWith({
    int? id,
    String? title,
    String? subtitle,
    String? author,
    String? narrator,
    String? series,
    double? seriesPosition,
    String? description,
    String? genre,
    String? normalizedGenre,
    String? tags,
    int? publishYear,
    int? copyrightYear,
    String? publisher,
    String? isbn,
    String? asin,
    String? language,
    double? rating,
    double? userRating,
    double? averageRating,
    int? abridged,
    String? coverImage,
    int? fileCount,
    int? isMultiFile,
    String? createdAt,
    int? duration,
    Progress? progress,
    List<Chapter>? chapters,
    bool? isFavorite,
  }) {
    return Audiobook(
      id: id ?? this.id,
      title: title ?? this.title,
      subtitle: subtitle ?? this.subtitle,
      author: author ?? this.author,
      narrator: narrator ?? this.narrator,
      series: series ?? this.series,
      seriesPosition: seriesPosition ?? this.seriesPosition,
      description: description ?? this.description,
      genre: genre ?? this.genre,
      normalizedGenre: normalizedGenre ?? this.normalizedGenre,
      tags: tags ?? this.tags,
      publishYear: publishYear ?? this.publishYear,
      copyrightYear: copyrightYear ?? this.copyrightYear,
      publisher: publisher ?? this.publisher,
      isbn: isbn ?? this.isbn,
      asin: asin ?? this.asin,
      language: language ?? this.language,
      rating: rating ?? this.rating,
      userRating: userRating ?? this.userRating,
      averageRating: averageRating ?? this.averageRating,
      abridged: abridged ?? this.abridged,
      coverImage: coverImage ?? this.coverImage,
      fileCount: fileCount ?? this.fileCount,
      isMultiFile: isMultiFile ?? this.isMultiFile,
      createdAt: createdAt ?? this.createdAt,
      duration: duration ?? this.duration,
      progress: progress ?? this.progress,
      chapters: chapters ?? this.chapters,
      isFavorite: isFavorite ?? this.isFavorite,
    );
  }

  String get formattedSeriesPosition {
    if (seriesPosition == null) return '';
    if (seriesPosition == seriesPosition!.toInt().toDouble()) {
      return seriesPosition!.toInt().toString();
    }
    return seriesPosition.toString();
  }
}

/// Progress tracking matching Android's Progress data class
class Progress {
  final int? id;
  final int? userId;
  final int? audiobookId;
  final int position;
  final int completed;
  final String? lastListened;
  final int? currentChapter;

  Progress({
    this.id,
    this.userId,
    this.audiobookId,
    required this.position,
    required this.completed,
    this.lastListened,
    this.currentChapter,
  });

  factory Progress.fromJson(Map<String, dynamic> json) {
    return Progress(
      id: json['id'],
      userId: json['user_id'],
      audiobookId: json['audiobook_id'],
      position: json['position'] ?? 0,
      completed: json['completed'] ?? 0,
      lastListened: json['last_listened'],
      currentChapter: json['current_chapter'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'user_id': userId,
      'audiobook_id': audiobookId,
      'position': position,
      'completed': completed,
      'last_listened': lastListened,
      'current_chapter': currentChapter,
    };
  }
}

/// Chapter model
class Chapter {
  final int id;
  final int? audiobookId;
  final int? fileId;
  final double startTime;
  final double? endTime;
  final String? title;
  final double? duration;

  Chapter({
    required this.id,
    this.audiobookId,
    this.fileId,
    required this.startTime,
    this.endTime,
    this.title,
    this.duration,
  });

  factory Chapter.fromJson(Map<String, dynamic> json) {
    return Chapter(
      id: Audiobook._parseInt(json['id']) ?? 0,
      audiobookId: Audiobook._parseInt(json['audiobook_id']) ?? 0,
      fileId: Audiobook._parseInt(json['file_id']),
      startTime: _parseDouble(json['start_time']) ?? 0,
      endTime: _parseDouble(json['end_time']),
      title: json['title'],
      duration: _parseDouble(json['duration']),
    );
  }

  static double? _parseDouble(dynamic value) {
    if (value == null) return null;
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'audiobook_id': audiobookId,
      'file_id': fileId,
      'start_time': startTime,
      'end_time': endTime,
      'title': title,
      'duration': duration,
    };
  }
}

/// Directory file model for audio files
class DirectoryFile {
  final int id;
  final int audiobookId;
  final String name;
  final String path;
  final int size;
  final int? duration;
  final int? trackNumber;

  DirectoryFile({
    required this.id,
    required this.audiobookId,
    required this.name,
    required this.path,
    required this.size,
    this.duration,
    this.trackNumber,
  });

  factory DirectoryFile.fromJson(Map<String, dynamic> json) {
    return DirectoryFile(
      id: Audiobook._parseInt(json['id']) ?? 0,
      audiobookId: Audiobook._parseInt(json['audiobook_id']) ?? 0,
      name: json['name'] ?? '',
      path: json['path'] ?? '',
      size: Audiobook._parseInt(json['size']) ?? 0,
      duration: Audiobook._parseInt(json['duration']),
      trackNumber: Audiobook._parseInt(json['track_number']),
    );
  }

  String get formattedSize {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024)
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
