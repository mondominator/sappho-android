import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:audio_service/audio_service.dart';
import 'providers/auth_provider.dart';
import 'providers/home_provider.dart';
import 'providers/library_provider.dart';
import 'providers/search_provider.dart';
import 'providers/detail_provider.dart';
import 'providers/player_provider.dart';
import 'providers/profile_provider.dart';
import 'providers/connectivity_provider.dart';
import 'providers/download_provider.dart';
import 'screens/login/login_screen.dart';
import 'screens/main/main_screen.dart';
import 'services/api_service.dart';
import 'services/auth_repository.dart';
import 'services/audio_handler.dart';
import 'theme/app_theme.dart';

/// Global audio handler instance
late SapphoAudioHandler audioHandler;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize audio service for background playback and media notifications
  audioHandler = await AudioService.init(
    builder: () => SapphoAudioHandler(),
    config: const AudioServiceConfig(
      androidNotificationChannelId: 'com.sappho.audiobooks.audio',
      androidNotificationChannelName: 'Sappho Audio',
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: true,
    ),
  );

  runApp(const SapphoApp());
}

class SapphoApp extends StatelessWidget {
  const SapphoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // Connectivity provider - monitors network status
        ChangeNotifierProvider(create: (_) => ConnectivityProvider()),
        // Auth repository - singleton for secure storage
        Provider<AuthRepository>(create: (_) => AuthRepository()),
        // API service - depends on auth repository
        ProxyProvider<AuthRepository, ApiService>(
          update: (_, authRepo, __) => ApiService(authRepo),
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

/// Routes to login or main based on auth state
/// Uses StatefulWidget to preserve providers across auth state changes
class AuthGate extends StatefulWidget {
  const AuthGate({super.key});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  // Track if we've been authenticated to preserve providers
  bool _wasAuthenticated = false;
  Widget? _authenticatedScreen;

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthProvider>(
      builder: (context, auth, child) {
        // Show loading indicator while checking auth status
        if (auth.isCheckingAuth) {
          return const Scaffold(
            backgroundColor: Color(0xFF0A0E1A),
            body: Center(
              child: CircularProgressIndicator(color: Color(0xFF3B82F6)),
            ),
          );
        }

        if (auth.isAuthenticated) {
          // Only create providers once when first authenticated
          // Reuse existing widget to preserve provider state
          if (!_wasAuthenticated || _authenticatedScreen == null) {
            _wasAuthenticated = true;
            // Set API service on audio handler for Android Auto browsing
            final apiService = context.read<ApiService>();
            audioHandler.setApiService(apiService);
            _authenticatedScreen = MultiProvider(
              providers: [
                ChangeNotifierProvider(
                  create: (context) => HomeProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) => LibraryProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) => SearchProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) => DetailProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) => PlayerProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) => ProfileProvider(
                    context.read<ApiService>(),
                    context.read<AuthRepository>(),
                  ),
                ),
                ChangeNotifierProvider(
                  create: (context) =>
                      DownloadProvider(context.read<AuthRepository>()),
                ),
              ],
              child: const MainScreen(),
            );
          }
          return _authenticatedScreen!;
        } else {
          // Reset when logged out so providers are recreated on next login
          _wasAuthenticated = false;
          _authenticatedScreen = null;
          return const LoginScreen();
        }
      },
    );
  }
}
