import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:image_picker/image_picker.dart';
import '../../models/user.dart';
import '../../providers/auth_provider.dart';
import '../../providers/profile_provider.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Unified Profile screen with stats and settings
class ProfileScreen extends StatefulWidget {
  final VoidCallback? onBack;
  final VoidCallback? onEditProfile; // Keep for compatibility but not used
  final Function(String author)? onAuthorTap;
  final Function(String genre)? onGenreTap;
  final Function(int audiobookId)? onAudiobookTap;

  const ProfileScreen({
    super.key,
    this.onBack,
    this.onEditProfile,
    this.onAuthorTap,
    this.onGenreTap,
    this.onAudiobookTap,
  });

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Edit state
  bool _isEditingProfile = false;
  bool _isEditingPassword = false;
  bool _isSavingProfile = false;
  bool _isSavingPassword = false;
  bool _showCurrentPassword = false;
  bool _showNewPassword = false;

  // Controllers
  final _displayNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _currentPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_initialized) {
      _initialized = true;
      final auth = context.read<AuthProvider>();
      _displayNameController.text = auth.user?.displayName ?? '';
      _emailController.text = auth.user?.email ?? '';
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    _displayNameController.dispose();
    _emailController.dispose();
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  void _showMessage(String message, {bool isError = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? sapphoError : sapphoSuccess,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        margin: const EdgeInsets.all(16),
      ),
    );
  }

  Future<void> _saveProfile() async {
    setState(() => _isSavingProfile = true);
    try {
      final api = context.read<ApiService>();
      final auth = context.read<AuthProvider>();
      final updatedUser = await api.updateProfile(
        displayName: _displayNameController.text.isEmpty
            ? null
            : _displayNameController.text,
        email: _emailController.text.isEmpty ? null : _emailController.text,
      );
      auth.updateUser(updatedUser);
      setState(() {
        _isEditingProfile = false;
        _isSavingProfile = false;
      });
      _showMessage('Profile updated');
    } catch (e) {
      setState(() => _isSavingProfile = false);
      _showMessage('Failed to update profile', isError: true);
    }
  }

  Future<void> _savePassword() async {
    if (_currentPasswordController.text.isEmpty ||
        _newPasswordController.text.isEmpty ||
        _confirmPasswordController.text.isEmpty) {
      _showMessage('Please fill in all fields', isError: true);
      return;
    }
    if (_newPasswordController.text != _confirmPasswordController.text) {
      _showMessage('Passwords do not match', isError: true);
      return;
    }

    setState(() => _isSavingPassword = true);
    try {
      final api = context.read<ApiService>();
      await api.updatePassword(
        currentPassword: _currentPasswordController.text,
        newPassword: _newPasswordController.text,
      );
      setState(() {
        _isEditingPassword = false;
        _isSavingPassword = false;
      });
      _currentPasswordController.clear();
      _newPasswordController.clear();
      _confirmPasswordController.clear();
      _showMessage('Password updated');
    } catch (e) {
      setState(() => _isSavingPassword = false);
      _showMessage('Failed to change password', isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<ProfileProvider>(
      builder: (context, profile, child) {
        final auth = context.watch<AuthProvider>();
        final user = auth.user;
        final stats = profile.stats;

        return Container(
          color: sapphoBackground,
          child: SafeArea(
            top: false,
            child: NestedScrollView(
              headerSliverBuilder: (context, innerBoxIsScrolled) => [
                // Custom SliverAppBar with profile header
                SliverToBoxAdapter(
                  child: _buildHeader(context, profile, user, stats),
                ),
              ],
              body: Column(
                children: [
                  // Tab bar
                  Container(
                    color: sapphoSurface,
                    child: TabBar(
                      controller: _tabController,
                      indicatorColor: sapphoInfo,
                      indicatorWeight: 3,
                      labelColor: sapphoInfo,
                      unselectedLabelColor: sapphoTextMuted,
                      tabs: const [
                        Tab(text: 'Activity'),
                        Tab(text: 'Settings'),
                      ],
                    ),
                  ),
                  // Tab content
                  Expanded(
                    child: TabBarView(
                      controller: _tabController,
                      children: [
                        _buildActivityTab(profile, stats),
                        _buildSettingsTab(user),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildHeader(
    BuildContext context,
    ProfileProvider profile,
    User? user,
    UserStats? stats,
  ) {
    final topPadding = MediaQuery.of(context).padding.top;

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF1E3A5F), Color(0xFF0D1B2A)],
        ),
      ),
      child: Column(
        children: [
          // Top bar with back button
          Padding(
            padding: EdgeInsets.only(top: topPadding, left: 4, right: 16),
            child: Row(
              children: [
                IconButton(
                  onPressed: widget.onBack,
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                ),
                const Spacer(),
                // Refresh button
                IconButton(
                  onPressed: () => profile.refresh(),
                  icon: const Icon(
                    Icons.refresh,
                    color: Colors.white70,
                    size: 22,
                  ),
                ),
              ],
            ),
          ),

          // Profile info
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            child: Column(
              children: [
                // Avatar
                GestureDetector(
                  onTap: profile.isUploadingAvatar
                      ? null
                      : () => _showAvatarOptions(context, profile, user),
                  child: Stack(
                    children: [
                      Container(
                        width: 88,
                        height: 88,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: sapphoInfo.withValues(alpha: 0.5),
                            width: 3,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: sapphoInfo.withValues(alpha: 0.3),
                              blurRadius: 16,
                              spreadRadius: 2,
                            ),
                          ],
                        ),
                        child: ClipOval(
                          child: profile.isUploadingAvatar
                              ? Container(
                                  color: sapphoSurfaceLight,
                                  child: const Center(
                                    child: CircularProgressIndicator(
                                      color: sapphoInfo,
                                      strokeWidth: 2,
                                    ),
                                  ),
                                )
                              : _buildAvatar(user, profile),
                        ),
                      ),
                      Positioned(
                        bottom: 0,
                        right: 0,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: BoxDecoration(
                            color: sapphoInfo,
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: const Color(0xFF0D1B2A),
                              width: 2,
                            ),
                          ),
                          child: const Icon(
                            Icons.camera_alt,
                            color: Colors.white,
                            size: 14,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 12),

                // Name and username
                Text(
                  user?.displayName ?? user?.username ?? 'User',
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                Text(
                  '@${user?.username ?? ''}',
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.white.withValues(alpha: 0.6),
                  ),
                ),

                const SizedBox(height: 16),

                // Stats row
                if (stats != null && !profile.isLoadingStats)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _MiniStat(
                        value: _formatListenTime(stats.totalListenTime),
                        label: 'Listened',
                        icon: Icons.headphones,
                        color: sapphoInfo,
                      ),
                      _MiniStat(
                        value: stats.booksCompleted.toString(),
                        label: 'Completed',
                        icon: Icons.check_circle,
                        color: sapphoSuccess,
                      ),
                      _MiniStat(
                        value: stats.currentlyListening.toString(),
                        label: 'In Progress',
                        icon: Icons.play_circle,
                        color: sapphoWarning,
                      ),
                      _MiniStat(
                        value: '${stats.currentStreak}',
                        label: 'Day Streak',
                        icon: Icons.local_fire_department,
                        color: sapphoError,
                      ),
                    ],
                  )
                else if (profile.isLoadingStats)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        color: sapphoInfo,
                        strokeWidth: 2,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAvatar(User? user, ProfileProvider profile) {
    if (user?.avatar != null && profile.serverUrl != null) {
      final avatarUrl =
          '${profile.serverUrl}/api/profile/avatar?v=${user!.avatar.hashCode}';
      return CachedNetworkImage(
        imageUrl: avatarUrl,
        fit: BoxFit.cover,
        httpHeaders: profile.authToken != null
            ? {'Authorization': 'Bearer ${profile.authToken}'}
            : null,
        placeholder: (_, __) => _buildInitial(user),
        errorWidget: (_, __, ___) => _buildInitial(user),
      );
    }
    return _buildInitial(user);
  }

  Widget _buildInitial(User? user) {
    final initial = (user?.displayName ?? user?.username)?.isNotEmpty == true
        ? (user?.displayName ?? user?.username)![0].toUpperCase()
        : 'U';
    return Container(
      color: sapphoSurfaceLight,
      child: Center(
        child: Text(
          initial,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 32,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }

  void _showAvatarOptions(
    BuildContext context,
    ProfileProvider profile,
    User? user,
  ) {
    showModalBottomSheet(
      context: context,
      backgroundColor: sapphoSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.only(top: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt, color: sapphoInfo),
              title: const Text(
                'Take Photo',
                style: TextStyle(color: Colors.white),
              ),
              onTap: () async {
                Navigator.pop(ctx);
                final picker = ImagePicker();
                final image = await picker.pickImage(
                  source: ImageSource.camera,
                );
                if (image != null) await profile.uploadAvatar(File(image.path));
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library, color: sapphoInfo),
              title: const Text(
                'Choose from Gallery',
                style: TextStyle(color: Colors.white),
              ),
              onTap: () async {
                Navigator.pop(ctx);
                final picker = ImagePicker();
                final image = await picker.pickImage(
                  source: ImageSource.gallery,
                );
                if (image != null) await profile.uploadAvatar(File(image.path));
              },
            ),
            if (user?.avatar != null)
              ListTile(
                leading: const Icon(Icons.delete, color: sapphoError),
                title: const Text(
                  'Remove Photo',
                  style: TextStyle(color: sapphoError),
                ),
                onTap: () async {
                  Navigator.pop(ctx);
                  await profile.deleteAvatar();
                },
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _buildActivityTab(ProfileProvider profile, UserStats? stats) {
    if (profile.isLoadingStats) {
      return const Center(child: CircularProgressIndicator(color: sapphoInfo));
    }

    if (profile.statsError != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, color: sapphoError, size: 48),
            const SizedBox(height: 16),
            Text(
              'Failed to load stats',
              style: const TextStyle(color: Colors.white, fontSize: 16),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () => profile.loadStats(),
              style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (stats == null) {
      return const Center(
        child: Text(
          'No listening activity yet',
          style: TextStyle(color: sapphoTextMuted),
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Top Authors
        if (stats.topAuthors.isNotEmpty) ...[
          _SectionHeader(title: 'Top Authors', icon: Icons.person),
          const SizedBox(height: 8),
          ...stats.topAuthors
              .take(5)
              .map(
                (author) => _ActivityRow(
                  title: author.author,
                  subtitle: '${author.bookCount} books',
                  trailing: _formatListenTime(author.listenTime),
                  onTap: widget.onAuthorTap != null
                      ? () => widget.onAuthorTap!(author.author)
                      : null,
                ),
              ),
          const SizedBox(height: 20),
        ],

        // Top Genres
        if (stats.topGenres.isNotEmpty) ...[
          _SectionHeader(title: 'Top Genres', icon: Icons.category),
          const SizedBox(height: 8),
          ...stats.topGenres
              .take(5)
              .map(
                (genre) => _ActivityRow(
                  title: genre.genre,
                  subtitle: '${genre.bookCount} books',
                  trailing: _formatListenTime(genre.listenTime),
                  onTap: widget.onGenreTap != null
                      ? () => widget.onGenreTap!(genre.genre)
                      : null,
                ),
              ),
          const SizedBox(height: 20),
        ],

        // Recent Activity
        if (stats.recentActivity.isNotEmpty) ...[
          _SectionHeader(title: 'Recent Activity', icon: Icons.history),
          const SizedBox(height: 8),
          ...stats.recentActivity
              .take(10)
              .map(
                (item) => _RecentActivityRow(
                  item: item,
                  serverUrl: profile.serverUrl,
                  authToken: profile.authToken,
                  onTap: widget.onAudiobookTap != null
                      ? () => widget.onAudiobookTap!(item.id)
                      : null,
                ),
              ),
        ],
      ],
    );
  }

  Widget _buildSettingsTab(User? user) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Profile Section
        _SettingsCard(
          icon: Icons.person_outline,
          iconColor: sapphoInfo,
          title: 'Profile',
          trailing: _isEditingProfile
              ? null
              : _EditChip(
                  onTap: () => setState(() => _isEditingProfile = true),
                ),
          child: _isEditingProfile
              ? _buildProfileForm()
              : _buildProfileInfo(user),
        ),

        const SizedBox(height: 12),

        // Password Section
        _SettingsCard(
          icon: Icons.lock_outline,
          iconColor: sapphoWarning,
          title: 'Password',
          trailing: _isEditingPassword
              ? null
              : _EditChip(
                  onTap: () => setState(() => _isEditingPassword = true),
                ),
          child: _isEditingPassword
              ? _buildPasswordForm()
              : _buildPasswordInfo(),
        ),

        const SizedBox(height: 12),

        // Account Info Section
        _SettingsCard(
          icon: Icons.info_outline,
          iconColor: sapphoTextMuted,
          title: 'Account',
          child: Column(
            children: [
              _InfoRow(label: 'Username', value: user?.username ?? '—'),
              _InfoRow(
                label: 'Account Type',
                value: user?.isAdminUser == true ? 'Administrator' : 'User',
                valueColor: user?.isAdminUser == true ? sapphoSuccess : null,
              ),
              if (user?.createdAt != null)
                _InfoRow(
                  label: 'Member Since',
                  value: _formatDate(user!.createdAt!),
                ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildProfileInfo(User? user) {
    return Column(
      children: [
        _InfoRow(
          label: 'Display Name',
          value: user?.displayName ?? user?.username ?? '—',
        ),
        _InfoRow(label: 'Email', value: user?.email ?? 'Not set'),
      ],
    );
  }

  Widget _buildProfileForm() {
    return Column(
      children: [
        _TextField(
          controller: _displayNameController,
          label: 'Display Name',
          icon: Icons.badge_outlined,
        ),
        const SizedBox(height: 12),
        _TextField(
          controller: _emailController,
          label: 'Email',
          icon: Icons.email_outlined,
          keyboardType: TextInputType.emailAddress,
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: _ActionButton(
                label: 'Cancel',
                onTap: () => setState(() => _isEditingProfile = false),
                isOutlined: true,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _ActionButton(
                label: 'Save',
                onTap: _saveProfile,
                isLoading: _isSavingProfile,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPasswordInfo() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: List.generate(
            8,
            (_) => Container(
              margin: const EdgeInsets.only(right: 4),
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                color: Colors.white54,
                shape: BoxShape.circle,
              ),
            ),
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: sapphoSuccess.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(6),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.check_circle, color: sapphoSuccess, size: 14),
              SizedBox(width: 4),
              Text(
                'Secure',
                style: TextStyle(color: sapphoSuccess, fontSize: 12),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildPasswordForm() {
    return Column(
      children: [
        _TextField(
          controller: _currentPasswordController,
          label: 'Current Password',
          icon: Icons.lock_outline,
          isPassword: true,
          showPassword: _showCurrentPassword,
          onTogglePassword: () =>
              setState(() => _showCurrentPassword = !_showCurrentPassword),
        ),
        const SizedBox(height: 12),
        _TextField(
          controller: _newPasswordController,
          label: 'New Password',
          icon: Icons.lock_reset,
          isPassword: true,
          showPassword: _showNewPassword,
          onTogglePassword: () =>
              setState(() => _showNewPassword = !_showNewPassword),
        ),
        const SizedBox(height: 12),
        _TextField(
          controller: _confirmPasswordController,
          label: 'Confirm Password',
          icon: Icons.lock_reset,
          isPassword: true,
          showPassword: _showNewPassword,
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: _ActionButton(
                label: 'Cancel',
                onTap: () {
                  setState(() => _isEditingPassword = false);
                  _currentPasswordController.clear();
                  _newPasswordController.clear();
                  _confirmPasswordController.clear();
                },
                isOutlined: true,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _ActionButton(
                label: 'Update',
                onTap: _savePassword,
                isLoading: _isSavingPassword,
                color: sapphoWarning,
              ),
            ),
          ],
        ),
      ],
    );
  }

  String _formatListenTime(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    if (hours >= 24) {
      final days = hours ~/ 24;
      return '${days}d ${hours % 24}h';
    } else if (hours > 0) {
      return '${hours}h ${minutes}m';
    } else if (minutes > 0) {
      return '${minutes}m';
    }
    return '${seconds}s';
  }

  String _formatDate(String dateStr) {
    try {
      final date = DateTime.parse(dateStr);
      final months = [
        'Jan',
        'Feb',
        'Mar',
        'Apr',
        'May',
        'Jun',
        'Jul',
        'Aug',
        'Sep',
        'Oct',
        'Nov',
        'Dec',
      ];
      return '${months[date.month - 1]} ${date.year}';
    } catch (e) {
      return dateStr;
    }
  }
}

// ============ Widget Components ============

class _MiniStat extends StatelessWidget {
  final String value;
  final String label;
  final IconData icon;
  final Color color;

  const _MiniStat({
    required this.value,
    required this.label,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.15),
            shape: BoxShape.circle,
          ),
          child: Icon(icon, color: color, size: 18),
        ),
        const SizedBox(height: 6),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 16,
            fontWeight: FontWeight.bold,
          ),
        ),
        Text(
          label,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.6),
            fontSize: 11,
          ),
        ),
      ],
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final IconData icon;

  const _SectionHeader({required this.title, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: sapphoInfo, size: 18),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _ActivityRow extends StatelessWidget {
  final String title;
  final String subtitle;
  final String trailing;
  final VoidCallback? onTap;

  const _ActivityRow({
    required this.title,
    required this.subtitle,
    required this.trailing,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: sapphoSurfaceLight,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(color: Colors.white, fontSize: 14),
                  ),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      color: sapphoTextMuted,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            Text(
              trailing,
              style: const TextStyle(
                color: sapphoInfo,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
            if (onTap != null) ...[
              const SizedBox(width: 8),
              const Icon(Icons.chevron_right, color: sapphoTextMuted, size: 18),
            ],
          ],
        ),
      ),
    );
  }
}

class _RecentActivityRow extends StatelessWidget {
  final RecentActivity item;
  final String? serverUrl;
  final String? authToken;
  final VoidCallback? onTap;

  const _RecentActivityRow({
    required this.item,
    this.serverUrl,
    this.authToken,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: sapphoSurfaceLight,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(
          children: [
            // Cover
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: sapphoProgressTrack,
                borderRadius: BorderRadius.circular(6),
              ),
              clipBehavior: Clip.antiAlias,
              child: item.coverImage != null && serverUrl != null
                  ? CachedNetworkImage(
                      imageUrl: '$serverUrl/api/audiobooks/${item.id}/cover',
                      fit: BoxFit.cover,
                      httpHeaders: authToken != null
                          ? {'Authorization': 'Bearer $authToken'}
                          : null,
                      errorWidget: (_, __, ___) => _placeholder(),
                    )
                  : _placeholder(),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title,
                    style: const TextStyle(color: Colors.white, fontSize: 14),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (item.author != null)
                    Text(
                      item.author!,
                      style: const TextStyle(
                        color: sapphoTextMuted,
                        fontSize: 12,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                ],
              ),
            ),
            if (item.completed == 1)
              const Icon(Icons.check_circle, color: sapphoSuccess, size: 20)
            else if (item.duration != null && item.duration! > 0)
              Text(
                '${((item.position / item.duration!) * 100).toInt()}%',
                style: const TextStyle(color: sapphoInfo, fontSize: 12),
              ),
            if (onTap != null) ...[
              const SizedBox(width: 8),
              const Icon(Icons.chevron_right, color: sapphoTextMuted, size: 18),
            ],
          ],
        ),
      ),
    );
  }

  Widget _placeholder() {
    return Center(
      child: Text(
        item.title.isNotEmpty ? item.title[0].toUpperCase() : 'A',
        style: const TextStyle(color: sapphoTextMuted, fontSize: 16),
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final Widget? trailing;
  final Widget child;

  const _SettingsCard({
    required this.icon,
    required this.iconColor,
    required this.title,
    this.trailing,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: iconColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(icon, color: iconColor, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
          ),
          const Divider(color: sapphoProgressTrack, height: 1),
          Padding(padding: const EdgeInsets.all(14), child: child),
        ],
      ),
    );
  }
}

class _EditChip extends StatelessWidget {
  final VoidCallback onTap;

  const _EditChip({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: sapphoInfo.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(6),
        ),
        child: const Text(
          'Edit',
          style: TextStyle(
            color: sapphoInfo,
            fontSize: 12,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;

  const _InfoRow({required this.label, required this.value, this.valueColor});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(color: sapphoTextMuted, fontSize: 13),
          ),
          Text(
            value,
            style: TextStyle(
              color: valueColor ?? Colors.white,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

class _TextField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final IconData icon;
  final bool isPassword;
  final bool showPassword;
  final VoidCallback? onTogglePassword;
  final TextInputType? keyboardType;

  const _TextField({
    required this.controller,
    required this.label,
    required this.icon,
    this.isPassword = false,
    this.showPassword = false,
    this.onTogglePassword,
    this.keyboardType,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: sapphoBackground,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: sapphoProgressTrack),
      ),
      child: TextField(
        controller: controller,
        obscureText: isPassword && !showPassword,
        keyboardType: keyboardType,
        style: const TextStyle(color: Colors.white, fontSize: 14),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: sapphoTextMuted, fontSize: 13),
          prefixIcon: Icon(icon, color: sapphoTextMuted, size: 18),
          suffixIcon: isPassword && onTogglePassword != null
              ? IconButton(
                  icon: Icon(
                    showPassword ? Icons.visibility_off : Icons.visibility,
                    color: sapphoTextMuted,
                    size: 18,
                  ),
                  onPressed: onTogglePassword,
                )
              : null,
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

class _ActionButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final bool isOutlined;
  final bool isLoading;
  final Color? color;

  const _ActionButton({
    required this.label,
    required this.onTap,
    this.isOutlined = false,
    this.isLoading = false,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final buttonColor = color ?? sapphoInfo;

    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: isOutlined ? Colors.transparent : buttonColor,
          borderRadius: BorderRadius.circular(10),
          border: isOutlined ? Border.all(color: sapphoProgressTrack) : null,
        ),
        child: Center(
          child: isLoading
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    color: Colors.white,
                    strokeWidth: 2,
                  ),
                )
              : Text(
                  label,
                  style: TextStyle(
                    color: isOutlined ? sapphoTextMuted : Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
        ),
      ),
    );
  }
}
