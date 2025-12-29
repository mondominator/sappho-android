class User {
  final int id;
  final String username;
  final String? email;
  final String? displayName;
  final int isAdmin;
  final String? avatar;
  final String? createdAt;

  User({
    required this.id,
    required this.username,
    this.email,
    this.displayName,
    required this.isAdmin,
    this.avatar,
    this.createdAt,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'],
      username: json['username'],
      email: json['email'],
      displayName: json['display_name'],
      isAdmin: json['is_admin'] ?? 0,
      avatar: json['avatar'],
      createdAt: json['created_at'],
    );
  }

  bool get isAdminUser => isAdmin == 1;
}

/// User statistics matching Android's UserStats model
class UserStats {
  final int totalListenTime;
  final int booksCompleted;
  final int currentlyListening;
  final int currentStreak;
  final List<TopAuthor> topAuthors;
  final List<TopGenre> topGenres;
  final List<RecentActivity> recentActivity;

  UserStats({
    required this.totalListenTime,
    required this.booksCompleted,
    required this.currentlyListening,
    required this.currentStreak,
    required this.topAuthors,
    required this.topGenres,
    required this.recentActivity,
  });

  factory UserStats.fromJson(Map<String, dynamic> json) {
    return UserStats(
      // Server returns camelCase, support both formats
      totalListenTime:
          json['totalListenTime'] ?? json['total_listen_time'] ?? 0,
      booksCompleted: json['booksCompleted'] ?? json['books_completed'] ?? 0,
      currentlyListening:
          json['currentlyListening'] ?? json['currently_listening'] ?? 0,
      currentStreak: json['currentStreak'] ?? json['current_streak'] ?? 0,
      topAuthors:
          (json['topAuthors'] as List? ?? json['top_authors'] as List?)
              ?.map((e) => TopAuthor.fromJson(e))
              .toList() ??
          [],
      topGenres:
          (json['topGenres'] as List? ?? json['top_genres'] as List?)
              ?.map((e) => TopGenre.fromJson(e))
              .toList() ??
          [],
      recentActivity:
          (json['recentActivity'] as List? ?? json['recent_activity'] as List?)
              ?.map((e) => RecentActivity.fromJson(e))
              .toList() ??
          [],
    );
  }
}

class TopAuthor {
  final String author;
  final int bookCount;
  final int listenTime;

  TopAuthor({
    required this.author,
    required this.bookCount,
    required this.listenTime,
  });

  factory TopAuthor.fromJson(Map<String, dynamic> json) {
    return TopAuthor(
      author: json['author'] ?? '',
      // Server returns camelCase, support both formats
      bookCount: json['bookCount'] ?? json['book_count'] ?? 0,
      listenTime: json['listenTime'] ?? json['listen_time'] ?? 0,
    );
  }
}

class TopGenre {
  final String genre;
  final int bookCount;
  final int listenTime;

  TopGenre({
    required this.genre,
    required this.bookCount,
    required this.listenTime,
  });

  factory TopGenre.fromJson(Map<String, dynamic> json) {
    return TopGenre(
      genre: json['genre'] ?? '',
      // Server returns camelCase, support both formats
      bookCount: json['bookCount'] ?? json['book_count'] ?? 0,
      listenTime: json['listenTime'] ?? json['listen_time'] ?? 0,
    );
  }
}

class RecentActivity {
  final int id;
  final String title;
  final String? author;
  final String? coverImage;
  final int position;
  final int? duration;
  final int completed;

  RecentActivity({
    required this.id,
    required this.title,
    this.author,
    this.coverImage,
    required this.position,
    this.duration,
    required this.completed,
  });

  factory RecentActivity.fromJson(Map<String, dynamic> json) {
    return RecentActivity(
      id: json['id'] ?? 0,
      title: json['title'] ?? '',
      author: json['author'],
      // Server returns snake_case for this one
      coverImage: json['cover_image'] ?? json['coverImage'],
      position: json['position'] ?? 0,
      duration: json['duration'],
      completed: json['completed'] ?? 0,
    );
  }
}
