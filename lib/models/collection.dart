import 'audiobook.dart';

/// Collection model matching Android's Collection and CollectionDetail
class Collection {
  final int id;
  final String name;
  final String? description;
  final int bookCount;
  final String? coverUrl;
  final String? firstCover;
  final List<int>? bookIds;
  final int? isPublic;
  final int? isOwner;
  final String? creatorUsername;

  Collection({
    required this.id,
    required this.name,
    this.description,
    required this.bookCount,
    this.coverUrl,
    this.firstCover,
    this.bookIds,
    this.isPublic,
    this.isOwner,
    this.creatorUsername,
  });

  factory Collection.fromJson(Map<String, dynamic> json) {
    // Parse book_ids as List<int>
    List<int>? bookIds;
    final rawBookIds = json['book_ids'] ?? json['bookIds'];
    if (rawBookIds is List) {
      bookIds = rawBookIds
          .map((e) => e is int ? e : int.tryParse(e.toString()) ?? 0)
          .toList();
    }

    return Collection(
      id: json['id'],
      name: json['name'] ?? '',
      description: json['description'],
      bookCount: json['book_count'] ?? json['bookCount'] ?? 0,
      coverUrl: json['cover_url'] ?? json['coverUrl'],
      firstCover: json['first_cover'] ?? json['firstCover'],
      bookIds: bookIds,
      isPublic: json['is_public'] ?? json['isPublic'],
      isOwner: json['is_owner'] ?? json['isOwner'],
      creatorUsername: json['creator_username'] ?? json['creatorUsername'],
    );
  }
}

/// Collection detail with full book list
class CollectionDetail {
  final int id;
  final String name;
  final String? description;
  final int? isPublic;
  final int? isOwner;
  final List<Audiobook> books;

  CollectionDetail({
    required this.id,
    required this.name,
    this.description,
    this.isPublic,
    this.isOwner,
    required this.books,
  });

  factory CollectionDetail.fromJson(Map<String, dynamic> json) {
    final booksJson = json['books'] as List<dynamic>? ?? [];
    return CollectionDetail(
      id: json['id'],
      name: json['name'] ?? '',
      description: json['description'],
      isPublic: json['is_public'] ?? json['isPublic'],
      isOwner: json['is_owner'] ?? json['isOwner'],
      books: booksJson.map((b) => Audiobook.fromJson(b)).toList(),
    );
  }
}
