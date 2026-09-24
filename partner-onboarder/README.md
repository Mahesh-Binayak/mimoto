# Partner Onboarder

## Overview
Onboards the Mimoto partners (`mimoto-keybinding` and `mimoto-oidc`) and uploads their certificates. Refer to the [mosip-onboarding repo](https://github.com/mosip/mosip-onboarding).

The `install.sh` script wraps the `mosip/partner-onboarder` Helm chart (version `1.4.0`) and interactively collects the configuration it needs (report storage backend, namespace and SSL/domain status) before running the onboarding job.

## Prerequisites

### 1. Report storage (choose one)

You can store the onboarder's HTML reports either in **S3** or on an **NFS** share. The install script will ask which one you want to use.

**Option A - S3**
Have the following ready before running the script:
- S3 host
- S3 region
- S3 bucket name
- S3 access key
- S3 secret key (not echoed while typing)

**Option B - NFS**
Create a directory for the onboarder on the NFS server:
```
mkdir -p /srv/nfs/mosip/<sandbox>/onboarder/
```
Ensure the directory has `777` permissions:
```
chmod 777 /srv/nfs/mosip/<sandbox>/onboarder
```
Add the following entry to `/etc/exports`:
```
/srv/nfs/mosip/<sandbox>/onboarder *(rw,sync,no_root_squash,no_all_squash,insecure,subtree_check)
```
Apply the export and restart the NFS server:
```
sudo exportfs -rav
sudo systemctl restart nfs-kernel-server
```
Have the **NFS server IP** and the **NFS path** ready - the script will prompt for both.

### 2. Keycloak / PMS access

The script copies the `global`, `keycloak-env-vars` and `keycloak-host` configmaps and the required secrets into the onboarder namespace (`copy_cm.sh`, `copy_secrets.sh`). If you run the onboarder in a separate Inji cluster where PMS or Keycloak doesn't exist, uncomment and fill in the `extraEnvVars` block at the top of `values.yaml`.

### 3. SSL / domain

The script asks whether you have a public domain with a valid SSL certificate:
- **Y** - no extra flag is set.
- **n** - the script sets `onboarding.configmaps.onboarding.ENABLE_INSECURE=true` so the onboarder can talk to endpoints without valid SSL. Only recommended in test environments.

### 4. `values.yaml`

Set `values.yaml` to run the onboarder for the module(s) you need, and update the `propertiesOverride` block if custom partner values are required, e.g.:

```yaml
onboarding:
  modules:
    - name: mimoto-keybinding
      enabled: true
    - name: mimoto-oidc
      enabled: true

  propertiesOverride:
    mimoto-keybinding:
      POLICY_NAME: mpolicy-default-mimotokeybinding
      POLICY_GROUP_NAME: mpolicygroup-default-mimotokeybinding
      PARTNER_KC_USERNAME: mpartner-default-mimotokeybinding
      PARTNER_ORGANIZATION_NAME: IITB
      PARTNER_TYPE: Auth_Partner
      PARTNER_DOMAIN: Auth
    mimoto-oidc:
      POLICY_NAME: mpolicy-default-mimotooidc
      POLICY_GROUP_NAME: mpolicygroup-default-mimotooidc
      PARTNER_KC_USERNAME: mpartner-default-mimotooidc
      PARTNER_ORGANIZATION_NAME: IITB
      PARTNER_TYPE: Auth_Partner
      PARTNER_DOMAIN: Auth
      OIDC_CLIENT_NAME: mimoto-oidc
```

Only the keys listed override the onboarder image's baked-in defaults; see `properties/<module>.properties` in the mosip-onboarding repo for the full list of keys each module accepts. `propertiesOverride` is rendered into a plain ConfigMap, so don't put URLs, Keycloak admin credentials or client secrets there.

## Install

Run the script, optionally passing a kubeconfig path:
```
./install.sh [kubeconfig]
```

You'll be walked through:
1. **`values.yaml` confirmation** - confirm it's set correctly before proceeding.
2. **Report storage** - S3 details, or NFS server + path if S3 isn't available.
3. **Namespace** - the namespace to run the onboarder in.
4. **Public domain / SSL check** - `Y`/`n`.

The script then:
- Creates the namespace (if it doesn't already exist) and disables Istio sidecar injection on it.
- Copies the required configmaps and secrets.
- Installs the `mosip/partner-onboarder` chart (`mimoto-partner-onboarder`, version `1.4.0`), waiting for the jobs to complete.
- Copies the Mimoto wallet binding partner API key, Mimoto OIDC partner client ID and OIDC keystore password to `config-server` and restarts it.
- Cleans up the per-module properties configmaps created during the run.

## Troubleshooting

Once the onboarder job completes, a detailed HTML report is generated and stored in the S3 bucket or NFS directory you configured. Check this report to confirm the onboarding succeeded.

### Commonly found issues

1. **KER-ATH-401: Authentication Failed**
   Resolution: Provide the correct secret key for `mosip-deployment-client`.

2. **Certificate dates are not valid**
   Resolution: Check with the admin about adding a grace period in configuration.

3. **Upload of certificate will not be allowed to update other domain certificate**
   Resolution: This is expected when trying to upload the `ida-cred` certificate a second time - it should only run once, and this error can be ignored if the certificate is already present.

4. **Policy group / policy / partner already exists**
   Resolution: Pick a new `PARTNER_KC_USERNAME` / `POLICY_NAME` / `POLICY_GROUP_NAME` for the module in `propertiesOverride` and rerun.

5. **Script exits after the S3/NFS prompt**
   Resolution: Provide either complete S3 details or complete NFS details (server IP and path) and rerun the script.
