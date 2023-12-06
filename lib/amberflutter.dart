import 'amberflutter_platform_interface.dart';
import 'package:amberflutter/models.dart';

class Amberflutter {
  Future<Map<dynamic, dynamic>> getPublicKey({
    List<Permission>? permissions,
  }) {
    return AmberflutterPlatform.instance.getPublicKey(
      permissions: permissions,
    );
  }

  Future<Map<dynamic, dynamic>> signEvent({
    required String npub,
    required String event,
  }) {
    return AmberflutterPlatform.instance.signEvent(
      npub,
      event,
    );
  }

  Future<Map<dynamic, dynamic>> nip04Encrypt({
    required String plaintext,
    required String npub,
    required String pubkey,
  }) {
    return AmberflutterPlatform.instance.nip04Encrypt(
      plaintext,
      npub,
      pubkey,
    );
  }

  Future<Map<dynamic, dynamic>> nip04Decrypt({
    required String ciphertext,
    required String npub,
    required String pubkey,
  }) {
    return AmberflutterPlatform.instance.nip04Decrypt(
      ciphertext,
      npub,
      pubkey,
    );
  }

  Future<Map<dynamic, dynamic>> nip44Encrypt({
    required String plaintext,
    required String npub,
    required String pubkey,
  }) {
    return AmberflutterPlatform.instance.nip44Encrypt(
      plaintext,
      npub,
      pubkey,
    );
  }

  Future<Map<dynamic, dynamic>> nip44Decrypt({
    required String ciphertext,
    required String npub,
    required String pubkey,
  }) {
    return AmberflutterPlatform.instance.nip44Decrypt(
      ciphertext,
      npub,
      pubkey,
    );
  }
}
