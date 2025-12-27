class Audiobook {
  final int id;
  final String title;
  final String? author;
  final String? narrator;
  final String? series;
  final double? seriesPosition;
  final String? description;
  final String? genre;
  final int? publishYear;
  final String? coverImage;
  final int? duration;
  final int? currentPosition;
  final double? progress;
  final String? status;
  final List<Chapter>? chapters;

  Audiobook({
    required this.id,
    required this.title,
    this.author,
    this.narrator,
    this.series,
    this.seriesPosition,
    this.description,
    this.genre,
    this.publishYear,
    this.coverImage,
    this.duration,
    this.currentPosition,
    this.progress,
    this.status,
    this.chapters,
  });

  factory Audiobook.fromJson(Map<String, dynamic> json) {
    return Audiobook(
      id: json['id'],
      title: json['title'],
      author: json['author'],
      narrator: json['narrator'],
      series: json['series'],
      seriesPosition: json['series_position']?.toDouble(),
      description: json['description'],
      genre: json['genre'],
      publishYear: json['published_year'],
      coverImage: json['cover_image'],
      duration: json['duration'],
      currentPosition: json['current_position'],
      progress: json['progress']?.toDouble(),
      status: json['status'],
      chapters: json['chapters'] != null
          ? (json['chapters'] as List).map((c) => Chapter.fromJson(c)).toList()
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'author': author,
      'narrator': narrator,
      'series': series,
      'series_position': seriesPosition,
      'description': description,
      'genre': genre,
      'published_year': publishYear,
      'cover_image': coverImage,
      'duration': duration,
      'current_position': currentPosition,
      'progress': progress,
      'status': status,
      'chapters': chapters?.map((c) => c.toJson()).toList(),
    };
  }

  String get formattedSeriesPosition {
    if (seriesPosition == null) return '';
    if (seriesPosition == seriesPosition!.toInt().toDouble()) {
      return seriesPosition!.toInt().toString();
    }
    return seriesPosition.toString();
  }
}

class Chapter {
  final int id;
  final String? title;
  final double startTime;
  final double? endTime;

  Chapter({
    required this.id,
    this.title,
    required this.startTime,
    this.endTime,
  });

  factory Chapter.fromJson(Map<String, dynamic> json) {
    return Chapter(
      id: json['id'],
      title: json['title'],
      startTime: json['start_time'].toDouble(),
      endTime: json['end_time']?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'start_time': startTime,
      'end_time': endTime,
    };
  }
}
