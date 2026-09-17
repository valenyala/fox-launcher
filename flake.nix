{
  description = "Fox Launcher dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      platformVersion = "37";
      buildToolsVersion = "37.0.0";

      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ platformVersion ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = false; # set true + includeSystemImages/systemImageTypes/abiVersions for an emulator
        includeNDK = false;
        includeSources = false;
      };

      sdk = "${android.androidsdk}/libexec/android-sdk";
      jdk = pkgs.jdk17;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          jdk
          android.androidsdk
          pkgs.gradle
          pkgs.android-tools
        ];

        ANDROID_HOME = sdk;
        ANDROID_SDK_ROOT = sdk;
        JAVA_HOME = jdk;

        # NixOS ships a patched aapt2; AGP would otherwise unpack a non-runnable one from Maven.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";
      };

      formatter.${system} = pkgs.nixpkgs-fmt;
    };
}
