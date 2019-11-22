/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 04/10/2016.
 */

package cm.aptoide.pt.install;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.database.realm.Download;
import cm.aptoide.pt.database.realm.Installed;
import cm.aptoide.pt.downloadmanager.AptoideDownloadManager;
import cm.aptoide.pt.downloadmanager.DownloadNotFoundException;
import cm.aptoide.pt.downloadmanager.DownloadsRepository;
import cm.aptoide.pt.install.installer.DefaultInstaller;
import cm.aptoide.pt.install.installer.InstallationState;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.preferences.managed.ManagerPreferences;
import cm.aptoide.pt.preferences.secure.SecurePreferences;
import cm.aptoide.pt.root.RootAvailabilityManager;
import cm.aptoide.pt.utils.BroadcastRegisterOnSubscribe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.Subscription;
import rx.schedulers.Schedulers;

import static cm.aptoide.pt.install.InstallService.ACTION_INSTALL_FINISHED;
import static cm.aptoide.pt.install.InstallService.EXTRA_INSTALLATION_MD5;

/**
 * Created by marcelobenites on 9/29/16.
 */

public class InstallManager {

  private static final String TAG = "InstallManager";
  private final AptoideDownloadManager aptoideDownloadManager;
  private final Installer installer;
  private final SharedPreferences sharedPreferences;
  private final SharedPreferences securePreferences;
  private final Context context;
  private final PackageInstallerManager packageInstallerManager;
  private final DownloadsRepository downloadRepository;
  private final InstalledRepository installedRepository;
  private final RootAvailabilityManager rootAvailabilityManager;
  private final CrashReport crashReporter;
  private List<DownloadInstallationType> startedDownloadsList;
  private Subscription installCompletedDownloadsSubscription;

  public InstallManager(Context context, AptoideDownloadManager aptoideDownloadManager,
      Installer installer, RootAvailabilityManager rootAvailabilityManager,
      SharedPreferences sharedPreferences, SharedPreferences securePreferences,
      DownloadsRepository downloadRepository, InstalledRepository installedRepository,
      PackageInstallerManager packageInstallerManager, CrashReport crashReporter) {
    this.aptoideDownloadManager = aptoideDownloadManager;
    this.installer = installer;
    this.context = context;
    this.rootAvailabilityManager = rootAvailabilityManager;
    this.downloadRepository = downloadRepository;
    this.installedRepository = installedRepository;
    this.sharedPreferences = sharedPreferences;
    this.securePreferences = securePreferences;
    this.packageInstallerManager = packageInstallerManager;
    this.crashReporter = crashReporter;
  }

  public void start() {
    aptoideDownloadManager.start();
    startedDownloadsList = new ArrayList<>();

    installCompletedDownloads();
  }

  private void installCompletedDownloads() {
    installCompletedDownloadsSubscription = downloadRepository.getCompletedDownloads()
        .filter(downloads -> !downloads.isEmpty())
        .flatMapIterable(download -> download)
        .flatMapCompletable(
            download -> Observable.just(takeDownloadInstallationInfo(download.getMd5()))
                .filter(downloadInstallationType -> downloadInstallationType != null)
                .flatMapCompletable(
                    downloadInstallationType -> stopForegroundAndInstall(download.getAction(),
                        downloadInstallationType))
                .toCompletable()
                .andThen(sendBackgroundInstallFinishedBroadcast(download)))
        .retry()
        .subscribe(__ -> {
        }, throwable -> {
          throwable.printStackTrace();
          crashReporter.log(throwable);
        });
  }

  private Completable sendBackgroundInstallFinishedBroadcast(Download download) {
    return Completable.fromAction(() -> {
      context.sendBroadcast(
          new Intent(ACTION_INSTALL_FINISHED).putExtra(EXTRA_INSTALLATION_MD5, download.getMd5()));
    });
  }

  public void stop() {
    aptoideDownloadManager.stop();
    if (installCompletedDownloadsSubscription != null
        && !installCompletedDownloadsSubscription.isUnsubscribed()) {
      installCompletedDownloadsSubscription.unsubscribe();
    }
  }

  private Completable stopForegroundAndInstall(int downloadAction,
      DownloadInstallationType downloadInstallationType) {
    Logger.getInstance()
        .d(TAG, "going to pop install from: "
            + downloadInstallationType.getMd5()
            + "and download action: "
            + downloadAction);
    switch (downloadAction) {
      case Download.ACTION_INSTALL:
        return installer.install(context, downloadInstallationType.getMd5(),
            downloadInstallationType.getForceDefaultInstall(),
            downloadInstallationType.getShouldSetPackageInstaller());
      case Download.ACTION_UPDATE:
        return installer.update(context, downloadInstallationType.getMd5(),
            downloadInstallationType.getForceDefaultInstall(),
            downloadInstallationType.getShouldSetPackageInstaller());
      case Download.ACTION_DOWNGRADE:
        return installer.downgrade(context, downloadInstallationType.getMd5(),
            downloadInstallationType.getForceDefaultInstall(),
            downloadInstallationType.getShouldSetPackageInstaller());
      default:
        return Completable.error(
            new IllegalArgumentException("Invalid download action " + downloadAction));
    }
  }

  private DownloadInstallationType takeDownloadInstallationInfo(String md5) {
    int indexOfDownloadInstallationInfo = -1;

    for (int i = 0; i < startedDownloadsList.size(); i++) {
      if (md5.equals(startedDownloadsList.get(i)
          .getMd5())) {
        indexOfDownloadInstallationInfo = i;
      }
    }

    if (indexOfDownloadInstallationInfo != -1) {
      return startedDownloadsList.remove(indexOfDownloadInstallationInfo);
    } else {
      return null;
    }
  }

  public Completable cancelInstall(String md5, String packageName, int versionCode) {
    return pauseInstall(md5).andThen(installedRepository.remove(packageName, versionCode))
        .andThen(aptoideDownloadManager.removeDownload(md5))
        .doOnError(throwable -> throwable.printStackTrace());
  }

  public Completable pauseInstall(String md5) {
    return aptoideDownloadManager.pauseDownload(md5);
  }

  public Observable<List<Install>> getTimedOutInstallations() {
    return getInstallations().flatMap(installs -> Observable.from(installs)
        .filter(install -> install.getState()
            .equals(Install.InstallationStatus.INSTALLATION_TIMEOUT))
        .toList());
  }

  public Observable<List<Install>> getInstalledApps() {
    return installedRepository.getAllInstalled()
        .concatMap(downloadList -> Observable.from(downloadList)
            .flatMap(download -> getInstall(download.getPackageName(),
                download.getVersionCode()).first())
            .toList());
  }

  private Observable<Install> getInstall(String packageName, int versionCode) {
    return installedRepository.get(packageName, versionCode)
        .map(installed -> new Install(100, Install.InstallationStatus.INSTALLED,
            Install.InstallationType.INSTALLED, false, -1, null, installed.getPackageName(),
            installed.getVersionCode(), installed.getVersionName(), installed.getName(),
            installed.getIcon()));
  }

  public Observable<List<Install>> getInstallations() {
    return aptoideDownloadManager.getDownloadsList()
        .observeOn(Schedulers.io())
        .concatMap(downloadList -> Observable.from(downloadList)
            .flatMap(download -> getInstall(download.getMd5(), download.getPackageName(),
                download.getVersionCode()).first())
            .toList())
        .distinctUntilChanged()
        .map(installs -> sortList(installs));
  }

  private List<Install> sortList(List<Install> installs) {
    Collections.sort(installs, (install, t1) -> {
      int toReturn;
      if (install.getState() == Install.InstallationStatus.DOWNLOADING
          && !install.isIndeterminate()) {
        toReturn = 1;
      } else if (t1.getState() == Install.InstallationStatus.DOWNLOADING && !t1.isIndeterminate()) {
        toReturn = -1;
      } else {
        int diff = install.getState()
            .ordinal() - t1.getState()
            .ordinal();
        if (diff == 0) {
          toReturn = install.getPackageName()
              .compareTo(t1.getPackageName());
        } else {
          toReturn = diff;
        }
      }
      return toReturn;
    });
    Collections.reverse(installs);
    return installs;
  }

  public Observable<Install> getCurrentInstallation() {
    return aptoideDownloadManager.getCurrentInProgressDownload()
        .observeOn(Schedulers.io())
        .flatMap(download -> getInstall(download.getMd5(), download.getPackageName(),
            download.getVersionCode()).first())
        .distinctUntilChanged();
  }

  public Completable install(Download download) {
    return install(download, false, false);
  }

  private Completable defaultInstall(Download download) {
    return install(download, true, false);
  }

  public Completable splitInstall(Download download) {
    return install(download, false, true);
  }

  private Completable install(Download download, boolean forceDefaultInstall,
      boolean forceSplitInstall) {
    return aptoideDownloadManager.getDownload(download.getMd5())
        .first()
        .map(storedDownload -> updateDownloadAction(download, storedDownload))
        .retryWhen(errors -> createDownloadAndRetry(errors, download))
        .doOnNext(storedDownload -> {
          if (storedDownload.getOverallDownloadStatus() == Download.ERROR) {
            storedDownload.setOverallDownloadStatus(Download.INVALID_STATUS);
            downloadRepository.save(storedDownload);
          }
        })
        .flatMap(install -> installInBackground(download.getMd5(), forceDefaultInstall,
            packageInstallerManager.shouldSetInstallerPackageName(download) || forceSplitInstall))
        .first()
        .toCompletable();
  }

  public Observable<Install> getInstall(String md5, String packageName, int versioncode) {
    return Observable.combineLatest(aptoideDownloadManager.getDownloadsByMd5(md5),
        installer.getState(packageName, versioncode), getInstallationType(packageName, versioncode),
        (download, installationState, installationType) -> createInstall(download,
            installationState, md5, packageName, versioncode, installationType))
        .doOnNext(install -> Logger.getInstance()
            .d(TAG, install.toString()));
  }

  private Install createInstall(Download download, InstallationState installationState, String md5,
      String packageName, int versioncode, Install.InstallationType installationType) {
    return new Install(mapInstallation(download),
        mapInstallationStatus(download, installationState), installationType,
        mapIndeterminateState(download, installationState), getSpeed(download), md5, packageName,
        versioncode, getVersionName(download, installationState),
        getAppName(download, installationState), getAppIcon(download, installationState));
  }

  private String getVersionName(Download download, InstallationState installationState) {
    if (download != null) {
      return download.getVersionName();
    } else {
      return installationState.getVersionName();
    }
  }

  private String getAppIcon(Download download, InstallationState installationState) {
    if (download != null) {
      return download.getIcon();
    } else {
      return installationState.getIcon();
    }
  }

  private String getAppName(Download download, InstallationState installationState) {
    if (download != null) {
      return download.getAppName();
    } else {
      return installationState.getName();
    }
  }

  private int getSpeed(Download download) {
    if (download != null) {
      return download.getDownloadSpeed();
    } else {
      return 0;
    }
  }

  private boolean mapIndeterminateState(Download download, InstallationState installationState) {
    return mapIndeterminate(download) || mapInstallIndeterminate(installationState.getStatus(),
        installationState.getType(), download);
  }

  private Install.InstallationStatus mapInstallationStatus(Download download,
      InstallationState installationState) {

    if (installationState.getStatus() == Installed.STATUS_COMPLETED) {
      return Install.InstallationStatus.INSTALLED;
    }

    if (installationState.getStatus() == Installed.STATUS_INSTALLING
        && installationState.getType() != Installed.TYPE_DEFAULT) {
      return Install.InstallationStatus.INSTALLING;
    }

    if (installationState.getStatus() == Installed.STATUS_WAITING
        && download != null
        && download.getOverallDownloadStatus() == Download.COMPLETED) {
      return Install.InstallationStatus.DOWNLOADING;
    }

    if (installationState.getStatus() == Installed.STATUS_ROOT_TIMEOUT) {
      return Install.InstallationStatus.INSTALLATION_TIMEOUT;
    }

    return mapDownloadState(download);
  }

  private int mapInstallation(Download download) {
    int progress = 0;
    if (download != null) {
      progress = download.getOverallProgress();
      Logger.getInstance()
          .d(TAG, " download is not null "
              + progress
              + " state "
              + download.getOverallDownloadStatus());
    } else {
      Logger.getInstance()
          .d(TAG, " download is null");
    }
    return progress;
  }

  private boolean mapIndeterminate(Download download) {
    boolean isIndeterminate = false;
    if (download != null) {
      switch (download.getOverallDownloadStatus()) {
        case Download.IN_QUEUE:
        case Download.WAITING_TO_MOVE_FILES:
          isIndeterminate = true;
          break;
        case Download.BLOCK_COMPLETE:
        case Download.COMPLETED:
        case Download.CONNECTED:
        case Download.ERROR:
        case Download.FILE_MISSING:
        case Download.INVALID_STATUS:
        case Download.NOT_DOWNLOADED:
        case Download.PAUSED:
        case Download.PENDING:
        case Download.PROGRESS:
        case Download.RETRY:
        case Download.STARTED:
        case Download.WARN:
          isIndeterminate = false;
          break;
        default:
          isIndeterminate = false;
      }
    }
    return isIndeterminate;
  }

  private Install.InstallationStatus mapDownloadState(Download download) {
    Install.InstallationStatus status = Install.InstallationStatus.UNINSTALLED;
    if (download != null) {
      switch (download.getOverallDownloadStatus()) {
        case Download.INVALID_STATUS:
          status = Install.InstallationStatus.INITIAL_STATE;
          break;
        case Download.FILE_MISSING:
        case Download.NOT_DOWNLOADED:
        case Download.COMPLETED:
          status = Install.InstallationStatus.UNINSTALLED;
          break;
        case Download.PAUSED:
          status = Install.InstallationStatus.PAUSED;
          break;
        case Download.ERROR:
          switch (download.getDownloadError()) {
            case Download.GENERIC_ERROR:
              status = Install.InstallationStatus.GENERIC_ERROR;
              break;
            case Download.NOT_ENOUGH_SPACE_ERROR:
              status = Install.InstallationStatus.NOT_ENOUGH_SPACE_ERROR;
              break;
          }
          break;
        case Download.RETRY:
        case Download.STARTED:
        case Download.WARN:
        case Download.CONNECTED:
        case Download.BLOCK_COMPLETE:
        case Download.PROGRESS:
        case Download.PENDING:
        case Download.WAITING_TO_MOVE_FILES:
          status = Install.InstallationStatus.DOWNLOADING;
          break;
        case Download.IN_QUEUE:
          status = Install.InstallationStatus.IN_QUEUE;
          break;
      }
    } else {
      Logger.getInstance()
          .d(TAG, "mapping a null Download state");
    }
    return status;
  }

  private boolean mapInstallIndeterminate(int status, int type, Download download) {
    boolean isIndeterminate = false;
    switch (status) {
      case Installed.STATUS_UNINSTALLED:
      case Installed.STATUS_COMPLETED:
        isIndeterminate = false;
        break;
      case Installed.STATUS_INSTALLING:
      case Installed.STATUS_ROOT_TIMEOUT:
        isIndeterminate = type != Installed.TYPE_DEFAULT;
        break;
      case Installed.STATUS_WAITING:
        isIndeterminate =
            download != null && download.getOverallDownloadStatus() == Download.COMPLETED;
    }
    if (download != null && download.getOverallDownloadStatus() == Download.INVALID_STATUS) {
      isIndeterminate = true;
    }
    return isIndeterminate;
  }

  @NonNull private Download updateDownloadAction(Download download, Download storedDownload) {
    if (storedDownload.getAction() != download.getAction()) {
      storedDownload.setAction(download.getAction());
      downloadRepository.save(storedDownload);
    }
    return storedDownload;
  }

  private Observable<Throwable> createDownloadAndRetry(Observable<? extends Throwable> errors,
      Download download) {
    return errors.flatMap(throwable -> {
      if (throwable instanceof DownloadNotFoundException) {
        Logger.getInstance()
            .d(TAG, "saved the newly created download because the other one was null");
        downloadRepository.save(download);
        return Observable.just(throwable);
      } else {
        return Observable.error(throwable);
      }
    });
  }

  private Observable<Void> installInBackground(String md5, boolean forceDefaultInstall,
      boolean shouldSetPackageInstaller) {
    return waitBackgroundInstallationResult(md5).startWith(
        startBackgroundInstallation(md5, forceDefaultInstall, shouldSetPackageInstaller));
  }

  private Observable<Void> startBackgroundInstallation(String md5, boolean forceDefaultInstall,
      boolean shouldSetPackageInstaller) {
    return aptoideDownloadManager.getDownload(md5)
        .first()
        .doOnNext(download -> initInstallationProgress(download, forceDefaultInstall,
            shouldSetPackageInstaller))
        .doOnNext(__ -> startInstallService(md5, forceDefaultInstall, shouldSetPackageInstaller))
        .flatMapCompletable(download -> {
          if (download.getOverallDownloadStatus() == Download.COMPLETED) {
            return Completable.fromAction(() -> {
              Logger.getInstance()
                  .d(TAG,
                      "Saving an already completed download to trigger the download installation");
              downloadRepository.save(download);
            });
          } else {
            return aptoideDownloadManager.startDownload(download);
          }
        })
        .map(__ -> null);
  }

  private void startInstallService(String md5, boolean forceDefaultInstall,
      boolean shouldSetPackageInstaller) {
    Intent intent = new Intent(context, InstallService.class);
    intent.setAction(InstallService.ACTION_START_INSTALL);
    intent.putExtra(EXTRA_INSTALLATION_MD5, md5);
    intent.putExtra(InstallService.EXTRA_FORCE_DEFAULT_INSTALL, forceDefaultInstall);
    intent.putExtra(InstallService.EXTRA_SET_PACKAGE_INSTALLER, shouldSetPackageInstaller);
    if (installer instanceof DefaultInstaller) {
      intent.putExtra(InstallService.EXTRA_INSTALLER_TYPE, InstallService.INSTALLER_TYPE_DEFAULT);
    }
    context.startService(intent);
  }

  private Observable<Void> waitBackgroundInstallationResult(String md5) {
    return Observable.create(
        new BroadcastRegisterOnSubscribe(context, new IntentFilter(ACTION_INSTALL_FINISHED), null,
            null))
        .filter(intent -> intent != null && ACTION_INSTALL_FINISHED.equals(intent.getAction()))
        .first(intent -> md5.equals(intent.getStringExtra(EXTRA_INSTALLATION_MD5)))
        .map(intent -> null);
  }

  private void initInstallationProgress(Download download, boolean forceDefaultInstall,
      boolean shouldSetPackageInstaller) {
    startedDownloadsList.add(new DownloadInstallationType(download.getMd5(), forceDefaultInstall,
        shouldSetPackageInstaller));
    Installed installed = convertDownloadToInstalled(download);
    installedRepository.save(installed);
  }

  @NonNull private Installed convertDownloadToInstalled(Download download) {
    Installed installed = new Installed();
    installed.setPackageAndVersionCode(download.getPackageName() + download.getVersionCode());
    installed.setVersionCode(download.getVersionCode());
    installed.setVersionName(download.getVersionName());
    installed.setStatus(Installed.STATUS_WAITING);
    installed.setType(Installed.TYPE_UNKNOWN);
    installed.setPackageName(download.getPackageName());
    return installed;
  }

  public boolean showWarning() {
    boolean wasRootDialogShowed = SecurePreferences.isRootDialogShowed(securePreferences);
    boolean isRooted = rootAvailabilityManager.isRootAvailable()
        .toBlocking()
        .value();
    boolean canGiveRoot = ManagerPreferences.allowRootInstallation(securePreferences);
    return isRooted && !wasRootDialogShowed && !canGiveRoot;
  }

  public void rootInstallAllowed(boolean allowRoot) {
    SecurePreferences.setRootDialogShowed(true, securePreferences);
    ManagerPreferences.setAllowRootInstallation(allowRoot, sharedPreferences);
  }

  public Observable<Boolean> startInstalls(List<Download> downloads) {
    return Observable.from(downloads)
        .map(download -> install(download).toObservable())
        .toList()
        .flatMap(observables -> Observable.merge(observables))
        .toList()
        .map(installs -> true)
        .onErrorReturn(throwable -> false);
  }

  public Completable onAppInstalled(Installed installed) {
    return installedRepository.getAsList(installed.getPackageName())
        .first()
        .flatMapIterable(installeds -> {
          //in case of installation made outside of aptoide
          if (installeds.isEmpty()) {
            installeds.add(installed);
          }
          return installeds;
        })
        .flatMapCompletable(databaseInstalled -> {
          if (databaseInstalled.getVersionCode() == installed.getVersionCode()) {
            installed.setType(databaseInstalled.getType());
            installed.setStatus(Installed.STATUS_COMPLETED);
            return Completable.fromAction(() -> installedRepository.save(installed));
          } else {
            return installedRepository.remove(databaseInstalled.getPackageName(),
                databaseInstalled.getVersionCode());
          }
        })
        .toCompletable();
  }

  public Completable onAppRemoved(String packageName) {
    return installedRepository.getAsList(packageName)
        .first()
        .flatMapIterable(installeds -> installeds)
        .flatMapCompletable(
            installed -> installedRepository.remove(packageName, installed.getVersionCode()))
        .toCompletable();
  }

  private Observable<Install.InstallationType> getInstallationType(String packageName,
      int versionCode) {
    return installedRepository.getInstalled(packageName)
        .map(installed -> {
          if (installed == null) {
            return Install.InstallationType.INSTALL;
          } else if (installed.getVersionCode() == versionCode) {
            return Install.InstallationType.INSTALLED;
          } else if (installed.getVersionCode() > versionCode) {
            return Install.InstallationType.DOWNGRADE;
          } else {
            return Install.InstallationType.UPDATE;
          }
        })
        .doOnNext(installationType -> Logger.getInstance()
            .d("AptoideDownloadManager", " emiting installation type"));
  }

  public Completable onUpdateConfirmed(Installed installed) {
    return onAppInstalled(installed);
  }

  /**
   * The caller is responsible to make sure that the download exists already
   * this method should only be used when a download exists already(ex: resuming)
   *
   * @return the download object to be resumed or null if doesn't exists
   */
  public Single<Download> getDownload(String md5) {
    return downloadRepository.getDownload(md5)
        .first()
        .toSingle();
  }

  public Completable retryTimedOutInstallations() {
    return getTimedOutInstallations().first()
        .flatMapIterable(installs -> installs)
        .flatMapSingle(install -> getDownload(install.getMd5()))
        .flatMapCompletable(download -> defaultInstall(download))
        .toCompletable();
  }

  public Completable cleanTimedOutInstalls() {
    return getTimedOutInstallations().first()
        .flatMap(installs -> Observable.from(installs)
            .flatMap(install -> installedRepository.get(install.getPackageName(),
                install.getVersionCode())
                .first()
                .doOnNext(installed -> {
                  installed.setStatus(Installed.STATUS_UNINSTALLED);
                  installedRepository.save(installed);
                })))
        .toList()
        .toCompletable();
  }

  public Observable<List<Installed>> fetchInstalled() {
    return installedRepository.getAllInstalledSorted()
        .first()
        .flatMapIterable(list -> list)
        .filter(item -> !item.isSystemApp())
        .toList();
  }

  public Observable<Boolean> isInstalled(String packageName) {
    return installedRepository.isInstalled(packageName)
        .first();
  }

  public Observable<Install> filterInstalled(Install item) {
    return installedRepository.isInstalled(item.getPackageName())
        .first()
        .flatMap(isInstalled -> {
          if (isInstalled) {
            return Observable.empty();
          }
          return Observable.just(item);
        });
  }

  public boolean wasAppEverInstalled(String packageName) {
    return installedRepository.getInstallationsHistory()
        .first()
        .flatMapIterable(installation -> installation)
        .filter(installation -> packageName.equals(installation.getPackageName()))
        .toList()
        .flatMap(installations -> {
          if (installations.isEmpty()) {
            return Observable.just(Boolean.FALSE);
          } else {
            return Observable.just(Boolean.TRUE);
          }
        })
        .toBlocking()
        .first();
  }
}
