class User {
  final int id;
  final String username;
  final String? email;
  final String? displayName;
  final int isAdmin;
  final String? avatar;

  User({
    required this.id,
    required this.username,
    this.email,
    this.displayName,
    required this.isAdmin,
    this.avatar,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'],
      username: json['username'],
      email: json['email'],
      displayName: json['display_name'],
      isAdmin: json['is_admin'] ?? 0,
      avatar: json['avatar'],
    );
  }

  bool get isAdminUser => isAdmin == 1;
}
