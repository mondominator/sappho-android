import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../providers/auth_provider.dart';
import '../../providers/profile_provider.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Edit Profile screen - accessed via Edit button in Profile screen
/// Modern design with inline editing
class EditProfileScreen extends StatefulWidget {
  final VoidCallback? onBack;

  const EditProfileScreen({super.key, this.onBack});

  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  // Edit profile fields
  final _displayNameController = TextEditingController();
  final _emailController = TextEditingController();

  // Password fields
  final _currentPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  bool _initialized = false;
  bool _isEditingProfile = false;
  bool _isEditingPassword = false;
  bool _isSavingProfile = false;
  bool _isSavingPassword = false;
  bool _showCurrentPassword = false;
  bool _showNewPassword = false;

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

  @override
  void dispose() {
    _displayNameController.dispose();
    _emailController.dispose();
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
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
      _showMessage('Profile updated successfully');
    } catch (e) {
      setState(() => _isSavingProfile = false);
      _showMessage('Failed to update profile', isError: true);
    }
  }

  Future<void> _savePassword() async {
    if (_currentPasswordController.text.isEmpty ||
        _newPasswordController.text.isEmpty ||
        _confirmPasswordController.text.isEmpty) {
      _showMessage('Please fill in all password fields', isError: true);
      return;
    }
    if (_newPasswordController.text != _confirmPasswordController.text) {
      _showMessage('New passwords do not match', isError: true);
      return;
    }
    if (_newPasswordController.text.length < 6) {
      _showMessage('Password must be at least 6 characters', isError: true);
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
      _showMessage('Password changed successfully');
    } catch (e) {
      setState(() => _isSavingPassword = false);
      _showMessage('Failed to change password', isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final profile = context.watch<ProfileProvider>();
    final user = auth.user;
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: Column(
        children: [
          // Modern app bar with gradient
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF1E3A5F), Color(0xFF0D1B2A)],
              ),
            ),
            padding: EdgeInsets.only(
              top: MediaQuery.of(context).padding.top,
              left: 4,
              right: 16,
              bottom: 16,
            ),
            child: Row(
              children: [
                IconButton(
                  onPressed: widget.onBack,
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                ),
                const Expanded(
                  child: Text(
                    'Edit Profile',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Content
          Expanded(
            child: SingleChildScrollView(
              child: Column(
                children: [
                  // Avatar section with gradient background
                  Container(
                    width: double.infinity,
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Color(0xFF0D1B2A), sapphoBackground],
                      ),
                    ),
                    padding: const EdgeInsets.only(top: 8, bottom: 32),
                    child: Column(
                      children: [
                        // Avatar with edit button
                        Stack(
                          children: [
                            Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: sapphoInfo.withValues(alpha: 0.5),
                                  width: 3,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: sapphoInfo.withValues(alpha: 0.3),
                                    blurRadius: 20,
                                    spreadRadius: 2,
                                  ),
                                ],
                              ),
                              child: CircleAvatar(
                                radius: 50,
                                backgroundColor: sapphoSurfaceLight,
                                backgroundImage:
                                    user?.avatar != null &&
                                        profile.serverUrl != null
                                    ? CachedNetworkImageProvider(
                                        '${profile.serverUrl}${user!.avatar}',
                                        headers: profile.authToken != null
                                            ? {
                                                'Authorization':
                                                    'Bearer ${profile.authToken}',
                                              }
                                            : null,
                                      )
                                    : null,
                                child: user?.avatar == null
                                    ? Text(
                                        (user?.displayName ??
                                                user?.username ??
                                                '?')[0]
                                            .toUpperCase(),
                                        style: const TextStyle(
                                          fontSize: 36,
                                          fontWeight: FontWeight.bold,
                                          color: Colors.white,
                                        ),
                                      )
                                    : null,
                              ),
                            ),
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: GestureDetector(
                                onTap: () {
                                  // Avatar editing is handled in profile screen
                                  _showMessage(
                                    'Change avatar from Profile screen',
                                  );
                                },
                                child: Container(
                                  padding: const EdgeInsets.all(8),
                                  decoration: BoxDecoration(
                                    color: sapphoInfo,
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: sapphoBackground,
                                      width: 2,
                                    ),
                                  ),
                                  child: const Icon(
                                    Icons.camera_alt,
                                    color: Colors.white,
                                    size: 16,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          user?.displayName ?? user?.username ?? 'User',
                          style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        if (user?.email != null)
                          Text(
                            user!.email!,
                            style: const TextStyle(
                              fontSize: 14,
                              color: sapphoTextMuted,
                            ),
                          ),
                      ],
                    ),
                  ),

                  // Settings sections
                  Padding(
                    padding: EdgeInsets.fromLTRB(16, 0, 16, bottomPadding + 16),
                    child: Column(
                      children: [
                        // Profile Information Card
                        _ModernCard(
                          icon: Icons.person_outline,
                          iconColor: sapphoInfo,
                          title: 'Profile Information',
                          trailing: _isEditingProfile
                              ? null
                              : _EditButton(
                                  onTap: () =>
                                      setState(() => _isEditingProfile = true),
                                ),
                          child: _isEditingProfile
                              ? _ProfileEditForm(
                                  displayNameController: _displayNameController,
                                  emailController: _emailController,
                                  isSaving: _isSavingProfile,
                                  onSave: _saveProfile,
                                  onCancel: () =>
                                      setState(() => _isEditingProfile = false),
                                )
                              : _ProfileInfoDisplay(
                                  displayName: user?.displayName,
                                  username: user?.username,
                                  email: user?.email,
                                ),
                        ),

                        const SizedBox(height: 16),

                        // Password Card
                        _ModernCard(
                          icon: Icons.lock_outline,
                          iconColor: sapphoWarning,
                          title: 'Password & Security',
                          trailing: _isEditingPassword
                              ? null
                              : _EditButton(
                                  onTap: () =>
                                      setState(() => _isEditingPassword = true),
                                ),
                          child: _isEditingPassword
                              ? _PasswordEditForm(
                                  currentPasswordController:
                                      _currentPasswordController,
                                  newPasswordController: _newPasswordController,
                                  confirmPasswordController:
                                      _confirmPasswordController,
                                  showCurrentPassword: _showCurrentPassword,
                                  showNewPassword: _showNewPassword,
                                  onToggleCurrentPassword: () => setState(
                                    () => _showCurrentPassword =
                                        !_showCurrentPassword,
                                  ),
                                  onToggleNewPassword: () => setState(
                                    () => _showNewPassword = !_showNewPassword,
                                  ),
                                  isSaving: _isSavingPassword,
                                  onSave: _savePassword,
                                  onCancel: () {
                                    setState(() => _isEditingPassword = false);
                                    _currentPasswordController.clear();
                                    _newPasswordController.clear();
                                    _confirmPasswordController.clear();
                                  },
                                )
                              : const _PasswordInfoDisplay(),
                        ),

                        const SizedBox(height: 16),

                        // Account Info Card
                        _ModernCard(
                          icon: Icons.info_outline,
                          iconColor: sapphoTextMuted,
                          title: 'Account Information',
                          child: Column(
                            children: [
                              _InfoRow(
                                label: 'Username',
                                value: user?.username ?? '—',
                              ),
                              const Divider(
                                color: sapphoProgressTrack,
                                height: 24,
                              ),
                              _InfoRow(
                                label: 'Account Type',
                                value: user?.isAdminUser == true
                                    ? 'Administrator'
                                    : 'User',
                              ),
                              const Divider(
                                color: sapphoProgressTrack,
                                height: 24,
                              ),
                              _InfoRow(
                                label: 'Member Since',
                                value: user?.createdAt != null
                                    ? _formatDate(user!.createdAt!)
                                    : '—',
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
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
      return '${months[date.month - 1]} ${date.day}, ${date.year}';
    } catch (e) {
      return dateStr;
    }
  }
}

class _ModernCard extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final Widget? trailing;
  final Widget child;

  const _ModernCard({
    required this.icon,
    required this.iconColor,
    required this.title,
    this.trailing,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: sapphoSurfaceBorder.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: iconColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, color: iconColor, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    ),
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
          ),
          const Divider(color: sapphoProgressTrack, height: 1),
          // Content
          Padding(padding: const EdgeInsets.all(16), child: child),
        ],
      ),
    );
  }
}

class _EditButton extends StatelessWidget {
  final VoidCallback onTap;

  const _EditButton({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: sapphoInfo.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.edit, color: sapphoInfo, size: 14),
            SizedBox(width: 4),
            Text(
              'Edit',
              style: TextStyle(
                color: sapphoInfo,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProfileInfoDisplay extends StatelessWidget {
  final String? displayName;
  final String? username;
  final String? email;

  const _ProfileInfoDisplay({this.displayName, this.username, this.email});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _InfoRow(label: 'Display Name', value: displayName ?? username ?? '—'),
        const Divider(color: sapphoProgressTrack, height: 24),
        _InfoRow(label: 'Email', value: email ?? 'Not set'),
      ],
    );
  }
}

class _ProfileEditForm extends StatelessWidget {
  final TextEditingController displayNameController;
  final TextEditingController emailController;
  final bool isSaving;
  final VoidCallback onSave;
  final VoidCallback onCancel;

  const _ProfileEditForm({
    required this.displayNameController,
    required this.emailController,
    required this.isSaving,
    required this.onSave,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _ModernTextField(
          controller: displayNameController,
          label: 'Display Name',
          icon: Icons.badge_outlined,
        ),
        const SizedBox(height: 16),
        _ModernTextField(
          controller: emailController,
          label: 'Email',
          icon: Icons.email_outlined,
          keyboardType: TextInputType.emailAddress,
        ),
        const SizedBox(height: 20),
        Row(
          children: [
            Expanded(
              child: _ActionButton(
                label: 'Cancel',
                onTap: onCancel,
                isOutlined: true,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _ActionButton(
                label: 'Save Changes',
                onTap: onSave,
                isLoading: isSaving,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _PasswordInfoDisplay extends StatelessWidget {
  const _PasswordInfoDisplay();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Password',
                style: TextStyle(fontSize: 13, color: sapphoTextMuted),
              ),
              const SizedBox(height: 4),
              Row(
                children: List.generate(
                  8,
                  (index) => Container(
                    margin: const EdgeInsets.only(right: 4),
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
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
                style: TextStyle(
                  color: sapphoSuccess,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _PasswordEditForm extends StatelessWidget {
  final TextEditingController currentPasswordController;
  final TextEditingController newPasswordController;
  final TextEditingController confirmPasswordController;
  final bool showCurrentPassword;
  final bool showNewPassword;
  final VoidCallback onToggleCurrentPassword;
  final VoidCallback onToggleNewPassword;
  final bool isSaving;
  final VoidCallback onSave;
  final VoidCallback onCancel;

  const _PasswordEditForm({
    required this.currentPasswordController,
    required this.newPasswordController,
    required this.confirmPasswordController,
    required this.showCurrentPassword,
    required this.showNewPassword,
    required this.onToggleCurrentPassword,
    required this.onToggleNewPassword,
    required this.isSaving,
    required this.onSave,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _ModernTextField(
          controller: currentPasswordController,
          label: 'Current Password',
          icon: Icons.lock_outline,
          isPassword: true,
          showPassword: showCurrentPassword,
          onTogglePassword: onToggleCurrentPassword,
        ),
        const SizedBox(height: 16),
        _ModernTextField(
          controller: newPasswordController,
          label: 'New Password',
          icon: Icons.lock_reset,
          isPassword: true,
          showPassword: showNewPassword,
          onTogglePassword: onToggleNewPassword,
        ),
        const SizedBox(height: 16),
        _ModernTextField(
          controller: confirmPasswordController,
          label: 'Confirm New Password',
          icon: Icons.lock_reset,
          isPassword: true,
          showPassword: showNewPassword,
          onTogglePassword: onToggleNewPassword,
        ),
        const SizedBox(height: 20),
        Row(
          children: [
            Expanded(
              child: _ActionButton(
                label: 'Cancel',
                onTap: onCancel,
                isOutlined: true,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _ActionButton(
                label: 'Update Password',
                onTap: onSave,
                isLoading: isSaving,
                color: sapphoWarning,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _ModernTextField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final IconData icon;
  final bool isPassword;
  final bool showPassword;
  final VoidCallback? onTogglePassword;
  final TextInputType? keyboardType;

  const _ModernTextField({
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
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: sapphoProgressTrack),
      ),
      child: TextField(
        controller: controller,
        obscureText: isPassword && !showPassword,
        keyboardType: keyboardType,
        style: const TextStyle(color: Colors.white),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: sapphoTextMuted, fontSize: 14),
          prefixIcon: Icon(icon, color: sapphoTextMuted, size: 20),
          suffixIcon: isPassword
              ? IconButton(
                  icon: Icon(
                    showPassword ? Icons.visibility_off : Icons.visibility,
                    color: sapphoTextMuted,
                    size: 20,
                  ),
                  onPressed: onTogglePassword,
                )
              : null,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 16,
          ),
          floatingLabelBehavior: FloatingLabelBehavior.auto,
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
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: isOutlined ? Colors.transparent : buttonColor,
          borderRadius: BorderRadius.circular(12),
          border: isOutlined ? Border.all(color: sapphoProgressTrack) : null,
        ),
        child: Center(
          child: isLoading
              ? SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    color: isOutlined ? sapphoTextMuted : Colors.white,
                    strokeWidth: 2,
                  ),
                )
              : Text(
                  label,
                  style: TextStyle(
                    color: isOutlined ? sapphoTextMuted : Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 14, color: sapphoTextMuted),
        ),
        Text(
          value,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: Colors.white,
          ),
        ),
      ],
    );
  }
}
