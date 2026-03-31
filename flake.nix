{
  description = "Shulker Accessories; equip shulker boxes as accessories for NeoForge 1.21.1";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle
        ];

        JAVA_HOME = "${pkgs.jdk21}";
      };

      ## nix run -- build JAR into dist/ ##
      apps.${system}.default = {
        type = "app";
        program = toString (
          pkgs.writeShellScript "build" ''
            set -euo pipefail
            export JAVA_HOME="${pkgs.jdk21}"

            ${pkgs.gradle}/bin/gradle --no-daemon jar

            mkdir -p dist
            find build/libs -maxdepth 1 -name "*.jar" \
              -not -name "*-sources.jar" \
              -exec cp {} dist/ \;

            echo "Built:"
            ls dist/*.jar
          ''
        );
      };
    };
}
