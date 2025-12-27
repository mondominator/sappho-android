import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'providers/auth_provider.dart';
import 'screens/home/home_screen.dart';
import 'screens/login/login_screen.dart';
import 'services/auth_repository.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SapphoApp());
}

class SapphoApp extends StatelessWidget {
  const SapphoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // Auth repository - singleton for secure storage
        Provider<AuthRepository>(
          create: (_) => AuthRepository(),
        ),
        // Auth provider - manages auth state
        ChangeNotifierProxyProvider<AuthRepository, AuthProvider>(
          create: (context) => AuthProvider(context.read<AuthRepository>()),
          update: (context, repo, previous) => previous ?? AuthProvider(repo),
        ),
      ],
      child: MaterialApp(
        title: 'Sappho',
        debugShowCheckedModeBanner: false,
        theme: sapphoTheme,
        home: const AuthGate(),
      ),
    );
  }
}

/// Routes to login or home based on auth state
class AuthGate extends StatelessWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthProvider>(
      builder: (context, auth, child) {
        if (auth.isAuthenticated) {
          return const MainScaffold();
        } else {
          return const LoginScreen();
        }
      },
    );
  }
}

/// Main app scaffold with bottom navigation
class MainScaffold extends StatefulWidget {
  const MainScaffold({super.key});

  @override
  State<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<MainScaffold> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    HomeScreen(),
    _PlaceholderScreen(title: 'Library', icon: Icons.library_books),
    _PlaceholderScreen(title: 'Search', icon: Icons.search),
    _PlaceholderScreen(title: 'Profile', icon: Icons.person),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() => _currentIndex = index);
        },
        backgroundColor: SapphoColors.surface,
        indicatorColor: SapphoColors.primary.withValues(alpha: 0.2),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.library_books_outlined),
            selectedIcon: Icon(Icons.library_books),
            label: 'Library',
          ),
          NavigationDestination(
            icon: Icon(Icons.search_outlined),
            selectedIcon: Icon(Icons.search),
            label: 'Search',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}

class _PlaceholderScreen extends StatelessWidget {
  final String title;
  final IconData icon;

  const _PlaceholderScreen({required this.title, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 64, color: SapphoColors.textMuted),
            const SizedBox(height: 16),
            Text(
              '$title coming soon',
              style: const TextStyle(color: SapphoColors.textSecondary),
            ),
          ],
        ),
      ),
    );
  }
}
