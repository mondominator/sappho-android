import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:file_picker/file_picker.dart';
import '../../services/api_service.dart';
import '../../theme/app_theme.dart';

/// Admin screen with comprehensive server management
class AdminScreen extends StatefulWidget {
  final VoidCallback? onBack;

  const AdminScreen({super.key, this.onBack});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  AdminTab _selectedTab = AdminTab.statistics;
  bool _isScanning = false;
  String? _scanResult;

  // Statistics state
  bool _isLoadingStats = true;
  Map<String, dynamic>? _stats;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final api = context.read<ApiService>();

    try {
      final stats = await api.getServerStats();
      if (mounted) {
        setState(() {
          _stats = stats;
          _isLoadingStats = false;
        });
      }
    } catch (e) {
      debugPrint('Admin: Failed to load stats: $e');
      if (mounted) {
        setState(() => _isLoadingStats = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Container(
      color: sapphoBackground,
      child: Column(
        children: [
          // App bar
          Container(
            color: sapphoBackground,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: Row(
              children: [
                IconButton(
                  onPressed: widget.onBack,
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                ),
                const SizedBox(width: 8),
                const Text(
                  'Admin Panel',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),

          // Tab bar
          Container(
            color: sapphoSurfaceLight,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: AdminTab.values.map((tab) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: _TabChip(
                      tab: tab,
                      isSelected: _selectedTab == tab,
                      onTap: () => setState(() => _selectedTab = tab),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),

          // Content
          Expanded(
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(16, 16, 16, bottomPadding + 16),
              child: _buildTabContent(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabContent() {
    switch (_selectedTab) {
      case AdminTab.statistics:
        return _StatisticsTab(isLoading: _isLoadingStats, stats: _stats);
      case AdminTab.library:
        return _LibraryTab(
          isScanning: _isScanning,
          scanResult: _scanResult,
          onScan: () => _performScan(false),
          onForceRescan: () => _performScan(true),
        );
      case AdminTab.jobs:
        return const _JobsTab();
      case AdminTab.users:
        return const _UsersTab();
      case AdminTab.logs:
        return const _LogsTab();
      case AdminTab.ai:
        return const _AISettingsTab();
      case AdminTab.email:
        return const _EmailSettingsTab();
      case AdminTab.apiKeys:
        return const _APIKeysTab();
      case AdminTab.backup:
        return const _BackupTab();
    }
  }

  Future<void> _performScan(bool force) async {
    setState(() {
      _isScanning = true;
      _scanResult = null;
    });

    try {
      final api = context.read<ApiService>();
      if (force) {
        await api.refreshLibrary();
        if (mounted) {
          setState(() {
            _isScanning = false;
            _scanResult = 'Full library refresh started in background';
          });
        }
      } else {
        await api.scanLibrary();
        if (mounted) {
          setState(() {
            _isScanning = false;
            _scanResult = 'Library scan completed';
          });
        }
      }
      _loadData();
    } catch (e) {
      debugPrint('Admin: Scan failed: $e');
      if (mounted) {
        setState(() {
          _isScanning = false;
          _scanResult = 'Error: ${e.toString()}';
        });
      }
    }
  }
}

enum AdminTab {
  statistics('Stats', Icons.bar_chart_outlined),
  library('Library', Icons.library_books_outlined),
  jobs('Jobs', Icons.work_outline),
  users('Users', Icons.people_outlined),
  logs('Logs', Icons.description_outlined),
  ai('AI', Icons.auto_awesome_outlined),
  email('Email', Icons.email_outlined),
  apiKeys('API Keys', Icons.key_outlined),
  backup('Backup', Icons.backup_outlined);

  final String title;
  final IconData icon;

  const AdminTab(this.title, this.icon);
}

class _TabChip extends StatelessWidget {
  final AdminTab tab;
  final bool isSelected;
  final VoidCallback onTap;

  const _TabChip({
    required this.tab,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? sapphoInfo : sapphoProgressTrack,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(tab.icon, color: Colors.white, size: 16),
            const SizedBox(width: 4),
            Text(
              tab.title,
              style: TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ============================================================================
// STATISTICS TAB
// ============================================================================

class _StatisticsTab extends StatelessWidget {
  final bool isLoading;
  final Map<String, dynamic>? stats;

  const _StatisticsTab({required this.isLoading, this.stats});

  String _formatDuration(dynamic seconds) {
    if (seconds == null) return '—';
    final totalSeconds = seconds is int ? seconds : (seconds as num).toInt();
    final hours = totalSeconds ~/ 3600;
    final days = hours ~/ 24;
    if (days > 0) {
      return '${days}d ${hours % 24}h';
    }
    return '${hours}h ${(totalSeconds % 3600) ~/ 60}m';
  }

  String _formatBytes(dynamic bytes) {
    if (bytes == null) return '—';
    final totalBytes = bytes is int ? bytes : (bytes as num).toDouble();
    if (totalBytes >= 1024 * 1024 * 1024) {
      return '${(totalBytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    } else if (totalBytes >= 1024 * 1024) {
      return '${(totalBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(totalBytes / 1024).toStringAsFixed(0)} KB';
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: CircularProgressIndicator(color: sapphoInfo),
        ),
      );
    }

    final totals = stats?['totals'] as Map<String, dynamic>?;
    final userStats = stats?['userStats'] as List<dynamic>?;
    final topAuthors = stats?['topAuthors'] as List<dynamic>?;
    final topSeries = stats?['topSeries'] as List<dynamic>?;
    final byFormat = stats?['byFormat'] as List<dynamic>?;

    return Column(
      children: [
        _AdminSectionCard(
          title: 'Library Overview',
          icon: Icons.analytics_outlined,
          children: [
            _StatRow(
              label: 'Total Audiobooks',
              value: totals?['books']?.toString() ?? '—',
            ),
            _StatRow(
              label: 'Total Duration',
              value: _formatDuration(totals?['duration']),
            ),
            _StatRow(
              label: 'Average Duration',
              value: _formatDuration(totals?['avgDuration']),
            ),
            _StatRow(
              label: 'Storage Used',
              value: _formatBytes(totals?['size']),
            ),
          ],
        ),
        if (byFormat != null && byFormat.isNotEmpty) ...[
          const SizedBox(height: 16),
          _AdminSectionCard(
            title: 'Storage by Format',
            icon: Icons.pie_chart_outline,
            children: [
              for (final fmt in byFormat.take(5))
                _StatRow(
                  label: (fmt['format']?.toString() ?? 'unknown').toUpperCase(),
                  value: '${fmt['count']} files (${_formatBytes(fmt['size'])})',
                ),
            ],
          ),
        ],
        if (topAuthors != null && topAuthors.isNotEmpty) ...[
          const SizedBox(height: 16),
          _AdminSectionCard(
            title: 'Top Authors',
            icon: Icons.person_outline,
            children: [
              for (final author in topAuthors.take(5))
                _StatRow(
                  label: author['author']?.toString() ?? 'Unknown',
                  value: '${author['count']} books',
                ),
            ],
          ),
        ],
        if (topSeries != null && topSeries.isNotEmpty) ...[
          const SizedBox(height: 16),
          _AdminSectionCard(
            title: 'Top Series',
            icon: Icons.collections_bookmark_outlined,
            children: [
              for (final series in topSeries.take(5))
                _StatRow(
                  label: series['series']?.toString() ?? 'Unknown',
                  value: '${series['count']} books',
                ),
            ],
          ),
        ],
        if (userStats != null && userStats.isNotEmpty) ...[
          const SizedBox(height: 16),
          _AdminSectionCard(
            title: 'User Activity',
            icon: Icons.people_outlined,
            children: [
              for (final user in userStats.take(5))
                _StatRow(
                  label: user['username']?.toString() ?? 'Unknown',
                  value:
                      '${user['booksCompleted'] ?? 0} completed, ${_formatDuration(user['totalListenTime'])}',
                ),
            ],
          ),
        ],
      ],
    );
  }
}

// ============================================================================
// LIBRARY TAB
// ============================================================================

class _LibraryTab extends StatelessWidget {
  final bool isScanning;
  final String? scanResult;
  final VoidCallback onScan;
  final VoidCallback onForceRescan;

  const _LibraryTab({
    required this.isScanning,
    this.scanResult,
    required this.onScan,
    required this.onForceRescan,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _AdminSectionCard(
          title: 'Library Actions',
          children: [
            _ActionButton(
              text: 'Scan Library',
              description: 'Scan for new audiobooks',
              icon: Icons.search_outlined,
              isLoading: isScanning,
              onPressed: onScan,
            ),
            const SizedBox(height: 12),
            _ActionButton(
              text: 'Refresh Library',
              description: 'Re-import all (preserves progress)',
              icon: Icons.refresh_outlined,
              isLoading: isScanning,
              onPressed: onForceRescan,
            ),
          ],
        ),
        if (scanResult != null) ...[
          const SizedBox(height: 16),
          _MessageBox(
            message: scanResult!,
            isError: scanResult!.contains('Error'),
          ),
        ],
      ],
    );
  }
}

// ============================================================================
// JOBS TAB (Background Jobs + Duplicates + Orphans)
// ============================================================================

class _JobsTab extends StatefulWidget {
  const _JobsTab();

  @override
  State<_JobsTab> createState() => _JobsTabState();
}

class _JobsTabState extends State<_JobsTab> {
  Map<String, dynamic>? _jobsStatus;
  Map<String, dynamic>? _duplicates;
  Map<String, dynamic>? _orphans;
  bool _isLoadingJobs = true;
  bool _isLoadingDuplicates = false;
  bool _isLoadingOrphans = false;
  bool _isMerging = false;
  bool _isDeletingOrphans = false;
  String? _message;
  bool _isError = false;

  @override
  void initState() {
    super.initState();
    _loadJobs();
  }

  Future<void> _loadJobs() async {
    try {
      final api = context.read<ApiService>();
      final status = await api.getJobsStatus();
      if (mounted)
        setState(() {
          _jobsStatus = status;
          _isLoadingJobs = false;
        });
    } catch (e) {
      if (mounted) setState(() => _isLoadingJobs = false);
    }
  }

  Future<void> _scanDuplicates() async {
    setState(() {
      _isLoadingDuplicates = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      final result = await api.getDuplicates();
      if (mounted)
        setState(() {
          _duplicates = result;
          _isLoadingDuplicates = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isLoadingDuplicates = false;
          _message = 'Failed to scan: $e';
          _isError = true;
        });
    }
  }

  Future<void> _scanOrphans() async {
    setState(() {
      _isLoadingOrphans = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      final result = await api.getOrphanDirectories();
      if (mounted)
        setState(() {
          _orphans = result;
          _isLoadingOrphans = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isLoadingOrphans = false;
          _message = 'Failed to scan: $e';
          _isError = true;
        });
    }
  }

  Future<void> _mergeDuplicate(Map<String, dynamic> group) async {
    final books = group['books'] as List<dynamic>;
    final keepId = group['suggestedKeep'] as int;
    final deleteIds = books
        .where((b) => b['id'] != keepId)
        .map((b) => b['id'] as int)
        .toList();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Merge Duplicates?',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          'Keep 1 book and delete ${deleteIds.length} duplicate(s). User progress will be preserved.',
          style: const TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Merge', style: TextStyle(color: sapphoWarning)),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() {
      _isMerging = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.mergeDuplicates(keepId: keepId, deleteIds: deleteIds);
      if (mounted) {
        setState(() {
          _isMerging = false;
          _message = 'Merged successfully';
          _isError = false;
        });
        _scanDuplicates();
      }
    } catch (e) {
      if (mounted)
        setState(() {
          _isMerging = false;
          _message = 'Failed to merge: $e';
          _isError = true;
        });
    }
  }

  Future<void> _deleteOrphan(String path) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Delete Directory?',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          'Delete: $path',
          style: const TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() {
      _isDeletingOrphans = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.deleteOrphanDirectories([path]);
      if (mounted) {
        setState(() {
          _isDeletingOrphans = false;
          _message = 'Deleted successfully';
          _isError = false;
        });
        _scanOrphans();
      }
    } catch (e) {
      if (mounted)
        setState(() {
          _isDeletingOrphans = false;
          _message = 'Failed to delete: $e';
          _isError = true;
        });
    }
  }

  String _formatTime(String? timestamp) {
    if (timestamp == null) return 'Never';
    try {
      final date = DateTime.parse(timestamp);
      final now = DateTime.now();
      final diff = now.difference(date);
      if (diff.inMinutes < 1) return 'Just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      return '${diff.inDays}d ago';
    } catch (_) {
      return timestamp;
    }
  }

  @override
  Widget build(BuildContext context) {
    final duplicateGroups =
        (_duplicates?['duplicateGroups'] as List<dynamic>?) ?? [];
    final orphanDirs = (_orphans?['orphanDirectories'] as List<dynamic>?) ?? [];

    return Column(
      children: [
        // Background Jobs Status
        _AdminSectionCard(
          title: 'Background Jobs',
          icon: Icons.schedule_outlined,
          children: [
            if (_isLoadingJobs)
              const Center(child: CircularProgressIndicator(color: sapphoInfo))
            else if (_jobsStatus != null) ...[
              for (final job
                  in (_jobsStatus!['jobs'] as Map<String, dynamic>? ?? {})
                      .entries)
                _StatRow(
                  label: job.key,
                  value: _formatTime(job.value?['lastRun']?.toString()),
                ),
              if (_jobsStatus!['forceRefreshInProgress'] == true)
                Container(
                  margin: const EdgeInsets.only(top: 8),
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: sapphoWarning.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    children: [
                      SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: sapphoWarning,
                        ),
                      ),
                      SizedBox(width: 8),
                      Text(
                        'Force rescan in progress...',
                        style: TextStyle(color: sapphoWarning, fontSize: 12),
                      ),
                    ],
                  ),
                ),
            ] else
              const Text(
                'No job information available',
                style: TextStyle(color: sapphoTextMuted),
              ),
          ],
        ),

        // Duplicates Section
        const SizedBox(height: 16),
        _AdminSectionCard(
          title: 'Duplicate Detection',
          icon: Icons.content_copy_outlined,
          children: [
            _ActionButton(
              text: 'Scan for Duplicates',
              description: 'Find duplicate audiobooks',
              icon: Icons.search_outlined,
              isLoading: _isLoadingDuplicates,
              onPressed: _scanDuplicates,
            ),
            if (duplicateGroups.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(
                'Found ${duplicateGroups.length} duplicate groups',
                style: const TextStyle(color: sapphoWarning, fontSize: 13),
              ),
              const SizedBox(height: 8),
              ...duplicateGroups
                  .take(5)
                  .map(
                    (group) => Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: sapphoProgressTrack,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            (group['books'] as List).first['title']
                                    ?.toString() ??
                                'Unknown',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.w500,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${(group['books'] as List).length} copies • ${group['matchReason']}',
                            style: const TextStyle(
                              color: sapphoTextMuted,
                              fontSize: 11,
                            ),
                          ),
                          const SizedBox(height: 8),
                          SizedBox(
                            width: double.infinity,
                            child: ElevatedButton(
                              onPressed: _isMerging
                                  ? null
                                  : () => _mergeDuplicate(group),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: sapphoInfo,
                                padding: const EdgeInsets.symmetric(
                                  vertical: 8,
                                ),
                              ),
                              child: _isMerging
                                  ? const SizedBox(
                                      width: 16,
                                      height: 16,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Text(
                                      'Merge',
                                      style: TextStyle(fontSize: 12),
                                    ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
            ],
          ],
        ),

        // Orphan Directories Section
        const SizedBox(height: 16),
        _AdminSectionCard(
          title: 'Orphan Directories',
          icon: Icons.folder_delete_outlined,
          children: [
            _ActionButton(
              text: 'Scan for Orphans',
              description: 'Find untracked directories',
              icon: Icons.search_outlined,
              isLoading: _isLoadingOrphans,
              onPressed: _scanOrphans,
            ),
            if (orphanDirs.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(
                'Found ${orphanDirs.length} orphan directories',
                style: const TextStyle(color: sapphoWarning, fontSize: 13),
              ),
              const SizedBox(height: 8),
              ...orphanDirs
                  .take(5)
                  .map(
                    (dir) => Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: sapphoProgressTrack,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.folder_outlined,
                            color: sapphoTextMuted,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  dir['relativePath']?.toString() ??
                                      dir['path']?.toString() ??
                                      'Unknown',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                Text(
                                  '${dir['fileCount']} files • ${dir['orphanType']}',
                                  style: const TextStyle(
                                    color: sapphoTextMuted,
                                    fontSize: 10,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            icon: Icon(
                              Icons.delete_outline,
                              color: sapphoError.withValues(alpha: 0.8),
                              size: 20,
                            ),
                            onPressed: _isDeletingOrphans
                                ? null
                                : () => _deleteOrphan(
                                    dir['path']?.toString() ?? '',
                                  ),
                          ),
                        ],
                      ),
                    ),
                  ),
            ],
          ],
        ),

        if (_message != null) ...[
          const SizedBox(height: 16),
          _MessageBox(message: _message!, isError: _isError),
        ],
      ],
    );
  }
}

// ============================================================================
// USERS TAB
// ============================================================================

class _UsersTab extends StatefulWidget {
  const _UsersTab();

  @override
  State<_UsersTab> createState() => _UsersTabState();
}

class _UsersTabState extends State<_UsersTab> {
  List<Map<String, dynamic>> _users = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadUsers();
  }

  Future<void> _loadUsers() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final api = context.read<ApiService>();
      final users = await api.getUsers();
      if (mounted)
        setState(() {
          _users = users;
          _isLoading = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _error = e.toString();
          _isLoading = false;
        });
    }
  }

  void _showCreateUserDialog() {
    final usernameController = TextEditingController();
    final passwordController = TextEditingController();
    final emailController = TextEditingController();
    bool isAdmin = false;
    bool isCreating = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: sapphoSurface,
          title: const Text(
            'Create User',
            style: TextStyle(color: Colors.white),
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: usernameController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Username *',
                    labelStyle: TextStyle(color: sapphoTextMuted),
                    enabledBorder: UnderlineInputBorder(
                      borderSide: BorderSide(color: sapphoSurfaceBorder),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: passwordController,
                  obscureText: true,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Password *',
                    labelStyle: TextStyle(color: sapphoTextMuted),
                    enabledBorder: UnderlineInputBorder(
                      borderSide: BorderSide(color: sapphoSurfaceBorder),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: emailController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Email',
                    labelStyle: TextStyle(color: sapphoTextMuted),
                    enabledBorder: UnderlineInputBorder(
                      borderSide: BorderSide(color: sapphoSurfaceBorder),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Checkbox(
                      value: isAdmin,
                      onChanged: (v) =>
                          setDialogState(() => isAdmin = v ?? false),
                      fillColor: WidgetStateProperty.all(sapphoInfo),
                    ),
                    const Text('Admin', style: TextStyle(color: Colors.white)),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isCreating
                  ? null
                  : () async {
                      if (usernameController.text.isEmpty ||
                          passwordController.text.isEmpty)
                        return;
                      setDialogState(() => isCreating = true);
                      try {
                        final api = context.read<ApiService>();
                        await api.createUser(
                          username: usernameController.text,
                          password: passwordController.text,
                          email: emailController.text.isNotEmpty
                              ? emailController.text
                              : null,
                          isAdmin: isAdmin,
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        _loadUsers();
                      } catch (e) {
                        setDialogState(() => isCreating = false);
                        if (ctx.mounted) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                            SnackBar(
                              content: Text('Failed: $e'),
                              backgroundColor: sapphoError,
                            ),
                          );
                        }
                      }
                    },
              style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              child: isCreating
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }

  void _showUserActions(Map<String, dynamic> user) {
    final userId = user['id'] as int;
    final isDisabled =
        user['account_disabled'] == true || user['account_disabled'] == 1;
    final isLocked = user['is_locked'] == true;
    final lockoutRemaining = user['lockout_remaining'] as int? ?? 0;

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
              leading: const Icon(Icons.person, color: sapphoInfo),
              title: Text(
                user['display_name'] ?? user['username'],
                style: const TextStyle(color: Colors.white),
              ),
              subtitle: Text(
                '@${user['username']}',
                style: const TextStyle(color: sapphoTextMuted),
              ),
            ),
            const Divider(color: sapphoSurfaceBorder),
            // Unlock option (for locked accounts)
            if (isLocked)
              ListTile(
                leading: const Icon(Icons.lock_open, color: sapphoSuccess),
                title: const Text(
                  'Unlock Account',
                  style: TextStyle(color: Colors.white),
                ),
                subtitle: Text(
                  'Locked for ${lockoutRemaining}s',
                  style: const TextStyle(color: sapphoTextMuted, fontSize: 12),
                ),
                onTap: () async {
                  Navigator.pop(ctx);
                  try {
                    final api = context.read<ApiService>();
                    await api.unlockUser(userId);
                    _loadUsers();
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Account unlocked'),
                          backgroundColor: sapphoSuccess,
                        ),
                      );
                    }
                  } catch (e) {
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Failed: $e'),
                          backgroundColor: sapphoError,
                        ),
                      );
                    }
                  }
                },
              ),
            // Enable/Disable
            ListTile(
              leading: Icon(
                isDisabled ? Icons.check_circle : Icons.block,
                color: isDisabled ? sapphoSuccess : sapphoWarning,
              ),
              title: Text(
                isDisabled ? 'Enable User' : 'Disable User',
                style: const TextStyle(color: Colors.white),
              ),
              onTap: () async {
                Navigator.pop(ctx);
                try {
                  final api = context.read<ApiService>();
                  if (isDisabled) {
                    await api.enableUser(userId);
                  } else {
                    await api.disableUser(userId);
                  }
                  _loadUsers();
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Failed: $e'),
                        backgroundColor: sapphoError,
                      ),
                    );
                  }
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete, color: sapphoError),
              title: const Text(
                'Delete User',
                style: TextStyle(color: sapphoError),
              ),
              onTap: () {
                Navigator.pop(ctx);
                _confirmDeleteUser(user);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  void _confirmDeleteUser(Map<String, dynamic> user) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Delete User?',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          'Delete ${user['username']}? This cannot be undone.',
          style: const TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                final api = context.read<ApiService>();
                await api.deleteUser(user['id']);
                _loadUsers();
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Failed: $e'),
                      backgroundColor: sapphoError,
                    ),
                  );
                }
              }
            },
            child: const Text('Delete', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: CircularProgressIndicator(color: sapphoInfo),
        ),
      );
    }

    if (_error != null) {
      return _AdminSectionCard(
        title: 'User Management',
        children: [
          Text('Error: $_error', style: const TextStyle(color: sapphoError)),
          const SizedBox(height: 16),
          ElevatedButton(onPressed: _loadUsers, child: const Text('Retry')),
        ],
      );
    }

    return _AdminSectionCard(
      title: 'User Management',
      icon: Icons.people_outlined,
      children: [
        if (_users.isEmpty)
          const Text('No users found', style: TextStyle(color: sapphoTextMuted))
        else
          ..._users.map(
            (user) => _UserRow(user: user, onTap: () => _showUserActions(user)),
          ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: _showCreateUserDialog,
            icon: const Icon(Icons.person_add_outlined, size: 18),
            label: const Text('Create User'),
            style: ElevatedButton.styleFrom(
              backgroundColor: sapphoInfo,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _UserRow extends StatelessWidget {
  final Map<String, dynamic> user;
  final VoidCallback onTap;

  const _UserRow({required this.user, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isAdmin = user['is_admin'] == 1;
    final isDisabled =
        user['account_disabled'] == true || user['account_disabled'] == 1;
    final isLocked = user['is_locked'] == true;

    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: const BoxDecoration(
          border: Border(
            bottom: BorderSide(color: sapphoSurfaceBorder, width: 0.5),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: isDisabled || isLocked
                    ? sapphoError.withValues(alpha: 0.2)
                    : sapphoInfo.withValues(alpha: 0.2),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  (user['username'] as String? ?? 'U')[0].toUpperCase(),
                  style: TextStyle(
                    color: isDisabled || isLocked ? sapphoError : sapphoInfo,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          user['display_name'] ?? user['username'],
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (isAdmin) ...[
                        const SizedBox(width: 6),
                        _Badge(text: 'Admin', color: sapphoWarning),
                      ],
                      if (isDisabled) ...[
                        const SizedBox(width: 6),
                        _Badge(text: 'Disabled', color: sapphoError),
                      ],
                      if (isLocked) ...[
                        const SizedBox(width: 6),
                        _Badge(text: 'Locked', color: sapphoError),
                      ],
                    ],
                  ),
                  Text(
                    '@${user['username']}',
                    style: const TextStyle(
                      color: sapphoTextMuted,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: sapphoTextMuted, size: 18),
          ],
        ),
      ),
    );
  }
}

// ============================================================================
// LOGS TAB
// ============================================================================

class _LogsTab extends StatefulWidget {
  const _LogsTab();

  @override
  State<_LogsTab> createState() => _LogsTabState();
}

class _LogsTabState extends State<_LogsTab> {
  List<dynamic> _logs = [];
  bool _isLoading = true;
  String? _selectedCategory;
  final _categories = [
    'All',
    'error',
    'success',
    'warning',
    'job',
    'library',
    'auth',
    'system',
    'info',
  ];

  @override
  void initState() {
    super.initState();
    _loadLogs();
  }

  Future<void> _loadLogs() async {
    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiService>();
      final result = await api.getServerLogs(limit: 200);
      if (mounted)
        setState(() {
          _logs = (result['logs'] as List?) ?? [];
          _isLoading = false;
        });
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _clearLogs() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text('Clear Logs?', style: TextStyle(color: Colors.white)),
        content: const Text(
          'This will clear all server logs.',
          style: TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Clear', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        final api = context.read<ApiService>();
        await api.clearServerLogs();
        _loadLogs();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed: $e'), backgroundColor: sapphoError),
          );
        }
      }
    }
  }

  Color _getCategoryColor(String? category) {
    switch (category) {
      case 'error':
        return sapphoError;
      case 'success':
        return sapphoSuccess;
      case 'warning':
        return sapphoWarning;
      case 'job':
        return sapphoInfo;
      case 'library':
        return Colors.purple;
      case 'auth':
        return Colors.orange;
      case 'system':
        return Colors.teal;
      default:
        return sapphoTextMuted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final filteredLogs = _selectedCategory == null || _selectedCategory == 'All'
        ? _logs
        : _logs
              .where(
                (l) =>
                    l['category'] == _selectedCategory ||
                    l['level'] == _selectedCategory,
              )
              .toList();

    return Column(
      children: [
        // Filter chips
        SizedBox(
          height: 36,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: _categories
                .map(
                  (cat) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      label: Text(
                        cat,
                        style: TextStyle(
                          color: _selectedCategory == cat
                              ? Colors.white
                              : sapphoTextMuted,
                          fontSize: 11,
                        ),
                      ),
                      selected:
                          _selectedCategory == cat ||
                          (cat == 'All' && _selectedCategory == null),
                      onSelected: (sel) => setState(
                        () => _selectedCategory = sel
                            ? (cat == 'All' ? null : cat)
                            : null,
                      ),
                      selectedColor: sapphoInfo,
                      backgroundColor: sapphoProgressTrack,
                      checkmarkColor: Colors.white,
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                )
                .toList(),
          ),
        ),
        const SizedBox(height: 12),
        // Action buttons
        Row(
          children: [
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _loadLogs,
                icon: const Icon(Icons.refresh, size: 16),
                label: const Text('Refresh'),
                style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _clearLogs,
                icon: const Icon(Icons.delete_outline, size: 16),
                label: const Text('Clear'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: sapphoError,
                  side: const BorderSide(color: sapphoError),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        // Logs list
        if (_isLoading)
          const Center(child: CircularProgressIndicator(color: sapphoInfo))
        else if (filteredLogs.isEmpty)
          const Text('No logs found', style: TextStyle(color: sapphoTextMuted))
        else
          ...filteredLogs
              .take(50)
              .map(
                (log) => Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: sapphoSurfaceLight,
                    borderRadius: BorderRadius.circular(8),
                    border: Border(
                      left: BorderSide(
                        color: _getCategoryColor(log['category']?.toString()),
                        width: 3,
                      ),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: _getCategoryColor(
                                log['category']?.toString(),
                              ).withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              (log['category']?.toString() ??
                                      log['level']?.toString() ??
                                      'info')
                                  .toUpperCase(),
                              style: TextStyle(
                                color: _getCategoryColor(
                                  log['category']?.toString(),
                                ),
                                fontSize: 9,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              log['timestamp']?.toString().substring(11, 19) ??
                                  '',
                              style: const TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 10,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        log['message']?.toString() ?? '',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                        ),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ),
      ],
    );
  }
}

// ============================================================================
// AI SETTINGS TAB
// ============================================================================

class _AISettingsTab extends StatefulWidget {
  const _AISettingsTab();

  @override
  State<_AISettingsTab> createState() => _AISettingsTabState();
}

class _AISettingsTabState extends State<_AISettingsTab> {
  bool _isLoading = true;
  bool _isSaving = false;
  bool _isTesting = false;
  String? _message;
  bool _isError = false;

  String _provider = 'openai';
  final _openaiKeyController = TextEditingController();
  String _openaiModel = 'gpt-4o-mini';
  final _geminiKeyController = TextEditingController();
  String _geminiModel = 'gemini-1.5-flash';
  bool _offensiveMode = false;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _openaiKeyController.dispose();
    _geminiKeyController.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    try {
      final api = context.read<ApiService>();
      final result = await api.getAISettings();
      final settings = result['settings'] as Map<String, dynamic>?;
      if (mounted && settings != null) {
        setState(() {
          _provider = settings['aiProvider']?.toString() ?? 'openai';
          _openaiKeyController.text =
              settings['openaiApiKey']?.toString() ?? '';
          _openaiModel = settings['openaiModel']?.toString() ?? 'gpt-4o-mini';
          _geminiKeyController.text =
              settings['geminiApiKey']?.toString() ?? '';
          _geminiModel =
              settings['geminiModel']?.toString() ?? 'gemini-1.5-flash';
          _offensiveMode = settings['recapOffensiveMode'] == true;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _saveSettings() async {
    setState(() {
      _isSaving = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.updateAISettings({
        'aiProvider': _provider,
        'openaiApiKey': _openaiKeyController.text,
        'openaiModel': _openaiModel,
        'geminiApiKey': _geminiKeyController.text,
        'geminiModel': _geminiModel,
        'recapOffensiveMode': _offensiveMode,
      });
      if (mounted)
        setState(() {
          _isSaving = false;
          _message = 'Settings saved';
          _isError = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isSaving = false;
          _message = 'Failed: $e';
          _isError = true;
        });
    }
  }

  Future<void> _testConnection() async {
    setState(() {
      _isTesting = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      final result = await api.testAIConnection({
        'aiProvider': _provider,
        'openaiApiKey': _openaiKeyController.text,
        'openaiModel': _openaiModel,
        'geminiApiKey': _geminiKeyController.text,
        'geminiModel': _geminiModel,
      });
      if (mounted)
        setState(() {
          _isTesting = false;
          _message = result['message']?.toString() ?? 'Connection successful';
          _isError = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isTesting = false;
          _message = 'Connection failed: $e';
          _isError = true;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator(color: sapphoInfo));
    }

    return Column(
      children: [
        _AdminSectionCard(
          title: 'AI Provider',
          icon: Icons.auto_awesome_outlined,
          children: [
            Row(
              children: [
                Expanded(
                  child: RadioListTile<String>(
                    title: const Text(
                      'OpenAI',
                      style: TextStyle(color: Colors.white, fontSize: 13),
                    ),
                    value: 'openai',
                    groupValue: _provider,
                    onChanged: (v) => setState(() => _provider = v ?? 'openai'),
                    activeColor: sapphoInfo,
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                  ),
                ),
                Expanded(
                  child: RadioListTile<String>(
                    title: const Text(
                      'Gemini',
                      style: TextStyle(color: Colors.white, fontSize: 13),
                    ),
                    value: 'gemini',
                    groupValue: _provider,
                    onChanged: (v) => setState(() => _provider = v ?? 'gemini'),
                    activeColor: sapphoInfo,
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 16),
        if (_provider == 'openai')
          _AdminSectionCard(
            title: 'OpenAI Settings',
            children: [
              TextField(
                controller: _openaiKeyController,
                style: const TextStyle(color: Colors.white, fontSize: 13),
                decoration: const InputDecoration(
                  labelText: 'API Key',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: _openaiModel,
                decoration: const InputDecoration(
                  labelText: 'Model',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
                dropdownColor: sapphoSurface,
                style: const TextStyle(color: Colors.white, fontSize: 13),
                items: ['gpt-4o-mini', 'gpt-4o', 'gpt-4-turbo', 'gpt-3.5-turbo']
                    .map((m) => DropdownMenuItem(value: m, child: Text(m)))
                    .toList(),
                onChanged: (v) =>
                    setState(() => _openaiModel = v ?? 'gpt-4o-mini'),
              ),
            ],
          )
        else
          _AdminSectionCard(
            title: 'Google Gemini Settings',
            children: [
              TextField(
                controller: _geminiKeyController,
                style: const TextStyle(color: Colors.white, fontSize: 13),
                decoration: const InputDecoration(
                  labelText: 'API Key',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: _geminiModel,
                decoration: const InputDecoration(
                  labelText: 'Model',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
                dropdownColor: sapphoSurface,
                style: const TextStyle(color: Colors.white, fontSize: 13),
                items: ['gemini-1.5-flash', 'gemini-1.5-pro', 'gemini-1.0-pro']
                    .map((m) => DropdownMenuItem(value: m, child: Text(m)))
                    .toList(),
                onChanged: (v) =>
                    setState(() => _geminiModel = v ?? 'gemini-1.5-flash'),
              ),
            ],
          ),
        const SizedBox(height: 16),
        _AdminSectionCard(
          title: 'Recap Options',
          children: [
            SwitchListTile(
              title: const Text(
                'Offensive Mode',
                style: TextStyle(color: Colors.white, fontSize: 13),
              ),
              subtitle: const Text(
                'Funny, irreverent recaps with profanity',
                style: TextStyle(color: sapphoTextMuted, fontSize: 11),
              ),
              value: _offensiveMode,
              onChanged: (v) => setState(() => _offensiveMode = v),
              activeColor: sapphoInfo,
              contentPadding: EdgeInsets.zero,
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: ElevatedButton(
                onPressed: _isTesting ? null : _testConnection,
                style: ElevatedButton.styleFrom(
                  backgroundColor: sapphoSurfaceLight,
                ),
                child: _isTesting
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Test Connection'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                onPressed: _isSaving ? null : _saveSettings,
                style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
                child: _isSaving
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Save Settings'),
              ),
            ),
          ],
        ),
        if (_message != null) ...[
          const SizedBox(height: 16),
          _MessageBox(message: _message!, isError: _isError),
        ],
      ],
    );
  }
}

// ============================================================================
// EMAIL SETTINGS TAB
// ============================================================================

class _EmailSettingsTab extends StatefulWidget {
  const _EmailSettingsTab();

  @override
  State<_EmailSettingsTab> createState() => _EmailSettingsTabState();
}

class _EmailSettingsTabState extends State<_EmailSettingsTab> {
  bool _isLoading = true;
  bool _isSaving = false;
  bool _isTesting = false;
  String? _message;
  bool _isError = false;

  bool _enabled = false;
  final _hostController = TextEditingController();
  final _portController = TextEditingController(text: '587');
  bool _secure = false;
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _fromAddressController = TextEditingController();
  final _fromNameController = TextEditingController(text: 'Sappho');

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _hostController.dispose();
    _portController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _fromAddressController.dispose();
    _fromNameController.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    try {
      final api = context.read<ApiService>();
      final settings = await api.getEmailSettings();
      if (mounted) {
        setState(() {
          _enabled = settings['enabled'] == true;
          _hostController.text = settings['host']?.toString() ?? '';
          _portController.text = settings['port']?.toString() ?? '587';
          _secure = settings['secure'] == true;
          _usernameController.text = settings['username']?.toString() ?? '';
          _passwordController.text = settings['password']?.toString() ?? '';
          _fromAddressController.text =
              settings['from_address']?.toString() ?? '';
          _fromNameController.text =
              settings['from_name']?.toString() ?? 'Sappho';
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _saveSettings() async {
    setState(() {
      _isSaving = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.updateEmailSettings({
        'enabled': _enabled,
        'host': _hostController.text,
        'port': int.tryParse(_portController.text) ?? 587,
        'secure': _secure,
        'username': _usernameController.text,
        'password': _passwordController.text,
        'from_address': _fromAddressController.text,
        'from_name': _fromNameController.text,
      });
      if (mounted)
        setState(() {
          _isSaving = false;
          _message = 'Settings saved';
          _isError = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isSaving = false;
          _message = 'Failed: $e';
          _isError = true;
        });
    }
  }

  Future<void> _testConnection() async {
    setState(() {
      _isTesting = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.testEmailConnection({
        'host': _hostController.text,
        'port': int.tryParse(_portController.text) ?? 587,
        'secure': _secure,
        'username': _usernameController.text,
        'password': _passwordController.text,
      });
      if (mounted)
        setState(() {
          _isTesting = false;
          _message = 'Connection successful';
          _isError = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isTesting = false;
          _message = 'Connection failed: $e';
          _isError = true;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator(color: sapphoInfo));
    }

    return Column(
      children: [
        _AdminSectionCard(
          title: 'Email Notifications',
          icon: Icons.email_outlined,
          children: [
            SwitchListTile(
              title: const Text(
                'Enable Email',
                style: TextStyle(color: Colors.white, fontSize: 13),
              ),
              subtitle: const Text(
                'Send email notifications to users',
                style: TextStyle(color: sapphoTextMuted, fontSize: 11),
              ),
              value: _enabled,
              onChanged: (v) => setState(() => _enabled = v),
              activeColor: sapphoInfo,
              contentPadding: EdgeInsets.zero,
            ),
          ],
        ),
        const SizedBox(height: 16),
        _AdminSectionCard(
          title: 'SMTP Configuration',
          children: [
            TextField(
              controller: _hostController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: const InputDecoration(
                labelText: 'SMTP Host',
                hintText: 'smtp.gmail.com',
                labelStyle: TextStyle(color: sapphoTextMuted),
                hintStyle: TextStyle(color: sapphoTextMuted),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: sapphoSurfaceBorder),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _portController,
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Port',
                      labelStyle: TextStyle(color: sapphoTextMuted),
                      enabledBorder: UnderlineInputBorder(
                        borderSide: BorderSide(color: sapphoSurfaceBorder),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Row(
                  children: [
                    Checkbox(
                      value: _secure,
                      onChanged: (v) => setState(() => _secure = v ?? false),
                      fillColor: WidgetStateProperty.all(sapphoInfo),
                    ),
                    const Text(
                      'SSL/TLS',
                      style: TextStyle(color: Colors.white, fontSize: 13),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _usernameController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: const InputDecoration(
                labelText: 'Username',
                labelStyle: TextStyle(color: sapphoTextMuted),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: sapphoSurfaceBorder),
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passwordController,
              obscureText: true,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: const InputDecoration(
                labelText: 'Password',
                labelStyle: TextStyle(color: sapphoTextMuted),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: sapphoSurfaceBorder),
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _fromAddressController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: const InputDecoration(
                labelText: 'From Address',
                hintText: 'noreply@example.com',
                labelStyle: TextStyle(color: sapphoTextMuted),
                hintStyle: TextStyle(color: sapphoTextMuted),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: sapphoSurfaceBorder),
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _fromNameController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: const InputDecoration(
                labelText: 'From Name',
                labelStyle: TextStyle(color: sapphoTextMuted),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: sapphoSurfaceBorder),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: ElevatedButton(
                onPressed: _isTesting ? null : _testConnection,
                style: ElevatedButton.styleFrom(
                  backgroundColor: sapphoSurfaceLight,
                ),
                child: _isTesting
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Test'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                onPressed: _isSaving ? null : _saveSettings,
                style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
                child: _isSaving
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Save'),
              ),
            ),
          ],
        ),
        if (_message != null) ...[
          const SizedBox(height: 16),
          _MessageBox(message: _message!, isError: _isError),
        ],
      ],
    );
  }
}

// ============================================================================
// API KEYS TAB
// ============================================================================

class _APIKeysTab extends StatefulWidget {
  const _APIKeysTab();

  @override
  State<_APIKeysTab> createState() => _APIKeysTabState();
}

class _APIKeysTabState extends State<_APIKeysTab> {
  List<Map<String, dynamic>> _keys = [];
  bool _isLoading = true;
  String? _newKey;

  @override
  void initState() {
    super.initState();
    _loadKeys();
  }

  Future<void> _loadKeys() async {
    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiService>();
      final keys = await api.getApiKeys();
      if (mounted)
        setState(() {
          _keys = keys;
          _isLoading = false;
        });
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showCreateKeyDialog() {
    final nameController = TextEditingController();
    int expiryDays = 90;
    bool isCreating = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: sapphoSurface,
          title: const Text(
            'Create API Key',
            style: TextStyle(color: Colors.white),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                  labelText: 'Name *',
                  hintText: 'My API Key',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  hintStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<int>(
                value: expiryDays,
                decoration: const InputDecoration(
                  labelText: 'Expires In',
                  labelStyle: TextStyle(color: sapphoTextMuted),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: sapphoSurfaceBorder),
                  ),
                ),
                dropdownColor: sapphoSurface,
                style: const TextStyle(color: Colors.white),
                items: const [
                  DropdownMenuItem(value: 30, child: Text('30 days')),
                  DropdownMenuItem(value: 90, child: Text('90 days')),
                  DropdownMenuItem(value: 180, child: Text('180 days')),
                  DropdownMenuItem(value: 365, child: Text('1 year')),
                ],
                onChanged: (v) => setDialogState(() => expiryDays = v ?? 90),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: isCreating
                  ? null
                  : () async {
                      if (nameController.text.isEmpty) return;
                      setDialogState(() => isCreating = true);
                      try {
                        final api = context.read<ApiService>();
                        final result = await api.createApiKey(
                          name: nameController.text,
                          expiresInDays: expiryDays,
                        );
                        if (ctx.mounted) Navigator.pop(ctx);
                        setState(() => _newKey = result['key']?.toString());
                        _loadKeys();
                      } catch (e) {
                        setDialogState(() => isCreating = false);
                        if (ctx.mounted) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                            SnackBar(
                              content: Text('Failed: $e'),
                              backgroundColor: sapphoError,
                            ),
                          );
                        }
                      }
                    },
              style: ElevatedButton.styleFrom(backgroundColor: sapphoInfo),
              child: isCreating
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleKey(Map<String, dynamic> key) async {
    try {
      final api = context.read<ApiService>();
      await api.updateApiKey(key['id'], isActive: key['is_active'] != 1);
      _loadKeys();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed: $e'), backgroundColor: sapphoError),
        );
      }
    }
  }

  Future<void> _deleteKey(Map<String, dynamic> key) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: sapphoSurface,
        title: const Text(
          'Delete API Key?',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          'Delete "${key['name']}"?',
          style: const TextStyle(color: sapphoTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: sapphoError)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        final api = context.read<ApiService>();
        await api.deleteApiKey(key['id']);
        _loadKeys();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed: $e'), backgroundColor: sapphoError),
          );
        }
      }
    }
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '—';
    try {
      final date = DateTime.parse(dateStr);
      return '${date.month}/${date.day}/${date.year}';
    } catch (_) {
      return dateStr;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // New key display
        if (_newKey != null) ...[
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: sapphoSuccess.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: sapphoSuccess.withValues(alpha: 0.3)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.check_circle, color: sapphoSuccess, size: 20),
                    SizedBox(width: 8),
                    Text(
                      'API Key Created',
                      style: TextStyle(
                        color: sapphoSuccess,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'Copy this key now - it won\'t be shown again!',
                  style: TextStyle(color: sapphoTextMuted, fontSize: 11),
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: sapphoSurface,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          _newKey!,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontFamily: 'monospace',
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(
                          Icons.copy,
                          color: sapphoInfo,
                          size: 18,
                        ),
                        onPressed: () {
                          Clipboard.setData(ClipboardData(text: _newKey!));
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Copied to clipboard'),
                              backgroundColor: sapphoSuccess,
                            ),
                          );
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: () => setState(() => _newKey = null),
                  child: const Text('Dismiss'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        _AdminSectionCard(
          title: 'API Keys',
          icon: Icons.key_outlined,
          children: [
            if (_isLoading)
              const Center(child: CircularProgressIndicator(color: sapphoInfo))
            else if (_keys.isEmpty)
              const Text(
                'No API keys',
                style: TextStyle(color: sapphoTextMuted),
              )
            else
              ..._keys.map(
                (key) => Container(
                  margin: const EdgeInsets.only(bottom: 12),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: sapphoProgressTrack,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Text(
                                  key['name']?.toString() ?? 'Unnamed',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                _Badge(
                                  text: key['is_active'] == 1
                                      ? 'Active'
                                      : 'Inactive',
                                  color: key['is_active'] == 1
                                      ? sapphoSuccess
                                      : sapphoTextMuted,
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              key['key_prefix']?.toString() ?? '',
                              style: const TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 11,
                                fontFamily: 'monospace',
                              ),
                            ),
                            Text(
                              'Expires: ${_formatDate(key['expires_at']?.toString())}',
                              style: const TextStyle(
                                color: sapphoTextMuted,
                                fontSize: 10,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: Icon(
                          key['is_active'] == 1
                              ? Icons.pause
                              : Icons.play_arrow,
                          color: sapphoInfo,
                          size: 20,
                        ),
                        onPressed: () => _toggleKey(key),
                      ),
                      IconButton(
                        icon: const Icon(
                          Icons.delete_outline,
                          color: sapphoError,
                          size: 20,
                        ),
                        onPressed: () => _deleteKey(key),
                      ),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: _showCreateKeyDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Create API Key'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: sapphoInfo,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

// ============================================================================
// BACKUP TAB
// ============================================================================

class _BackupTab extends StatefulWidget {
  const _BackupTab();

  @override
  State<_BackupTab> createState() => _BackupTabState();
}

class _BackupTabState extends State<_BackupTab> {
  List<Map<String, dynamic>> _backups = [];
  bool _isLoading = true;
  bool _isCreating = false;
  bool _isRestoring = false;
  String? _message;
  bool _isError = false;

  @override
  void initState() {
    super.initState();
    _loadBackups();
  }

  Future<void> _loadBackups() async {
    setState(() => _isLoading = true);
    try {
      final api = context.read<ApiService>();
      final backups = await api.getBackups();
      if (mounted)
        setState(() {
          _backups = backups;
          _isLoading = false;
        });
    } catch (e) {
      if (mounted)
        setState(() {
          _isLoading = false;
          _message = 'Failed to load backups: $e';
          _isError = true;
        });
    }
  }

  Future<void> _createBackup() async {
    setState(() {
      _isCreating = true;
      _message = null;
    });
    try {
      final api = context.read<ApiService>();
      await api.createBackup();
      if (mounted) {
        setState(() {
          _isCreating = false;
          _message = 'Backup created successfully';
          _isError = false;
        });
        _loadBackups();
      }
    } catch (e) {
      if (mounted)
        setState(() {
          _isCreating = false;
          _message = 'Failed: $e';
          _isError = true;
        });
    }
  }

  Future<void> _restoreBackup() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['db', 'sqlite', 'sqlite3'],
      );

      if (result != null && result.files.single.path != null) {
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            backgroundColor: sapphoSurface,
            title: const Text(
              'Restore Backup?',
              style: TextStyle(color: Colors.white),
            ),
            content: const Text(
              'This will replace all current data. This cannot be undone.',
              style: TextStyle(color: sapphoTextMuted),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text(
                  'Restore',
                  style: TextStyle(color: sapphoWarning),
                ),
              ),
            ],
          ),
        );

        if (confirmed == true && mounted) {
          setState(() {
            _isRestoring = true;
            _message = null;
          });
          final api = context.read<ApiService>();
          await api.restoreBackup(File(result.files.single.path!));
          if (mounted) {
            setState(() {
              _isRestoring = false;
              _message = 'Backup restored. Restart app to apply.';
              _isError = false;
            });
            _loadBackups();
          }
        }
      }
    } catch (e) {
      if (mounted)
        setState(() {
          _isRestoring = false;
          _message = 'Failed: $e';
          _isError = true;
        });
    }
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '—';
    try {
      final date = DateTime.parse(dateStr);
      return '${date.month}/${date.day}/${date.year} ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return dateStr;
    }
  }

  String _formatSize(dynamic bytes) {
    if (bytes == null) return '—';
    final size = bytes is int ? bytes : (bytes as num).toDouble();
    if (size >= 1024 * 1024)
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    if (size >= 1024) return '${(size / 1024).toStringAsFixed(0)} KB';
    return '$size B';
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _AdminSectionCard(
          title: 'Database Backup',
          icon: Icons.backup_outlined,
          children: [
            _ActionButton(
              text: 'Create Backup',
              description: 'Create a database backup',
              icon: Icons.download_outlined,
              isLoading: _isCreating,
              onPressed: _createBackup,
            ),
            const SizedBox(height: 12),
            _ActionButton(
              text: 'Restore Backup',
              description: 'Restore from a backup file',
              icon: Icons.upload_outlined,
              isLoading: _isRestoring,
              onPressed: _restoreBackup,
            ),
          ],
        ),
        if (_message != null) ...[
          const SizedBox(height: 16),
          _MessageBox(message: _message!, isError: _isError),
        ],
        if (!_isLoading && _backups.isNotEmpty) ...[
          const SizedBox(height: 16),
          _AdminSectionCard(
            title: 'Available Backups',
            icon: Icons.history,
            children: [
              ..._backups
                  .take(5)
                  .map(
                    (backup) => Container(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: const BoxDecoration(
                        border: Border(
                          bottom: BorderSide(
                            color: sapphoSurfaceBorder,
                            width: 0.5,
                          ),
                        ),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.description_outlined,
                            color: sapphoInfo,
                            size: 18,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  backup['filename']?.toString() ?? 'Unknown',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                  ),
                                ),
                                Text(
                                  '${_formatDate(backup['created_at']?.toString())} • ${_formatSize(backup['size'])}',
                                  style: const TextStyle(
                                    color: sapphoTextMuted,
                                    fontSize: 10,
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
        ],
      ],
    );
  }
}

// ============================================================================
// COMMON WIDGETS
// ============================================================================

class _AdminSectionCard extends StatelessWidget {
  final String title;
  final IconData? icon;
  final List<Widget> children;

  const _AdminSectionCard({
    required this.title,
    this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: sapphoSurfaceLight,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, color: sapphoInfo, size: 18),
                const SizedBox(width: 8),
              ],
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final String text;
  final String description;
  final IconData icon;
  final bool isLoading;
  final VoidCallback? onPressed;

  const _ActionButton({
    required this.text,
    required this.description,
    required this.icon,
    this.isLoading = false,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: isLoading ? null : onPressed,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: sapphoProgressTrack.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      color: sapphoInfo,
                      strokeWidth: 2,
                    ),
                  )
                : Icon(icon, color: sapphoInfo, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    text,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    description,
                    style: const TextStyle(
                      fontSize: 11,
                      color: sapphoIconDefault,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: sapphoTextMuted, size: 18),
          ],
        ),
      ),
    );
  }
}

class _StatRow extends StatelessWidget {
  final String label;
  final String value;

  const _StatRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Flexible(
            child: Text(
              label,
              style: const TextStyle(color: Colors.white, fontSize: 13),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            value,
            style: const TextStyle(color: sapphoIconDefault, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class _MessageBox extends StatelessWidget {
  final String message;
  final bool isError;

  const _MessageBox({required this.message, required this.isError});

  @override
  Widget build(BuildContext context) {
    final color = isError ? sapphoError : sapphoSuccess;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(
            isError ? Icons.error_outline : Icons.check_circle_outline,
            color: color,
            size: 18,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(message, style: TextStyle(color: color, fontSize: 12)),
          ),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String text;
  final Color color;

  const _Badge({required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 9,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}
