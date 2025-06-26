# Releasing

## Overview

kFSM uses automated publishing to Maven Central via GitHub Actions. The release process is triggered by creating a tag in semver format (e.g., `v0.10.4`) on the main branch.

## Prerequisites

Before releasing, ensure you have:
- Write access to the repository
- Access to the required GitHub secrets:
  - `SONATYPE_CENTRAL_USERNAME`
  - `SONATYPE_CENTRAL_PASSWORD`
  - `GPG_SECRET_KEY`
  - `GPG_SECRET_PASSPHRASE`

## Release Steps

### 1. Prepare the Release

1. Set the release version:

    ```sh
    export RELEASE_VERSION=A.B.C
    ```

2. Create a release branch:

    ```sh
    git checkout -b release/$RELEASE_VERSION
    ```

3. Update `CHANGELOG.md` with changes since the last release. Follow the existing `CHANGELOG.md` format, which is derived from [this guide](https://keepachangelog.com/en/1.0.0/)

4. Update the version in `gradle.properties`:

    ```sh
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    ```

5. Commit and push the release branch:

    ```sh
    git add .
    git commit -m "Prepare for release $RELEASE_VERSION"
    git push origin release/$RELEASE_VERSION
    ```

6. Create a pull request to merge the release branch into main:

    ```sh
    gh pr create --title "Release $RELEASE_VERSION" --body "Release version $RELEASE_VERSION"
    ```

7. Review and merge the pull request to main

### 2. Create and Push the Release Tag

Once the release PR is merged to main:

1. Pull the latest changes from main:

    ```sh
    git checkout main
    git pull origin main
    ```

2. Create a tag in semver format (must start with "v"):

    ```sh
    git tag -a v$RELEASE_VERSION -m "Release version $RELEASE_VERSION"
    git push origin v$RELEASE_VERSION
    ```

### 3. Automated Publishing

Once the tag is pushed, the [Publish to Maven Central](https://github.com/cashapp/kfsm/actions/workflows/publish.yml) workflow will automatically:

1. Build both artifacts:
   - `app.cash.kfsm:kfsm:$version` (core library)
   - `app.cash.kfsm:kfsm-guice:$version` (Guice integration)

2. Sign the artifacts with GPG

3. Publish to Maven Central via Sonatype

4. Generate and publish documentation to GitHub Pages

**Note**: It can take 10-30 minutes for artifacts to appear on Maven Central after successful publishing.

### 4. Create GitHub Release

1. Go to [GitHub Releases](https://github.com/cashapp/kfsm/releases/new)
2. Select the tag you just created (`v$RELEASE_VERSION`)
3. Copy the release notes from `CHANGELOG.md` into the release description
4. Publish the release

### 5. Prepare for Next Development Version

1. Create a new branch for the next development version:

    ```sh
    export NEXT_VERSION=A.B.D-SNAPSHOT
    git checkout -b next-version/$NEXT_VERSION
    ```

2. Update the version in `gradle.properties` to the next snapshot version:

    ```sh
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    ```

3. Commit and push the changes:

    ```sh
    git add .
    git commit -m "Prepare next development version"
    git push origin next-version/$NEXT_VERSION
    ```

4. Create a pull request to merge the next version branch into main:

    ```sh
    gh pr create --title "Prepare next development version" --body "Update version to $NEXT_VERSION"
    ```

5. Review and merge the pull request

## Troubleshooting

### Publishing Failures

- If the GitHub Action fails, check the workflow logs for specific error messages
- Common issues include:
  - Invalid GPG key or passphrase
  - Incorrect Sonatype credentials
  - Version conflicts (if the version was already published)
  - Network connectivity issues

### Manual Intervention

If the automated publishing fails and you need to manually intervene:

1. Check the [Sonatype Nexus](https://oss.sonatype.org/) staging repository
2. Drop any failed artifacts from the staging repository
3. Fix the issue and re-tag the release (delete the old tag first)
4. Re-run the workflow

### Access Issues

If you don't have access to the required secrets or Sonatype account, contact the project maintainers.

## Release Artifacts

Each release includes:

- **Core Library**: `app.cash.kfsm:kfsm:$version`
  - Main JAR with compiled classes
  - Sources JAR
  - Javadoc JAR
  - POM file

- **Guice Integration**: `app.cash.kfsm:kfsm-guice:$version`
  - Main JAR with compiled classes
  - Sources JAR
  - Javadoc JAR
  - POM file

All artifacts are signed with GPG and published to Maven Central.
