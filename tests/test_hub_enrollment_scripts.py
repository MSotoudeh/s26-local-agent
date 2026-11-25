"""The provisioning scripts never handle the shared Ops token and fail closed."""
import pathlib
import shutil
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
BOOTSTRAP = ROOT / "scripts/bootstrap-android-agent.ps1"
ISSUE = ROOT / "scripts/New-S26EnrollmentCode.ps1"
PWSH = shutil.which("pwsh")
HEX = "0123456789abcdef" * 4
CODE = f"pir1.s26.{HEX}.{HEX}"


def run(script: str) -> subprocess.CompletedProcess:
    return subprocess.run([PWSH, "-NoProfile", "-NonInteractive", "-Command", script],
                          capture_output=True, text=True, timeout=60)


class ProvisioningSource(unittest.TestCase):
    def test_bootstrap_does_not_copy_an_agent_token(self):
        text = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertNotIn("agent.json", text)
        self.assertNotIn("config.token", text)
        self.assertIn("enrollment_code = $code", text)

    def test_app_bootstrap_reads_a_code_not_a_token(self):
        source = (ROOT / "android/mobile-agent/app/src/main/java/io/projectir/mobile"
                  / "ConfigStore.java").read_text(encoding="utf-8")
        self.assertNotIn('optString("token"', source)
        self.assertIn('optString("enrollment_code"', source)


@unittest.skipUnless(PWSH, "PowerShell 7 is not installed")
class ProvisioningBehavior(unittest.TestCase):
    def bootstrap(self, code: str) -> subprocess.CompletedProcess:
        return run(f"$c = ConvertTo-SecureString '{code}' -AsPlainText -Force; "
                   f"& '{BOOTSTRAP}' -ApkPath '{BOOTSTRAP}' -EnrollmentCode $c")

    def test_code_for_another_device_is_refused_before_adb(self):
        result = self.bootstrap(CODE.replace("s26", "ops-box"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Not an enrollment code", result.stderr)
        self.assertNotIn(HEX, result.stdout + result.stderr)

    def test_raw_token_is_refused_before_adb(self):
        result = self.bootstrap(HEX)
        self.assertIn("Not an enrollment code", result.stderr)

    def test_issue_creates_the_device_first_and_never_prints_the_secrets(self):
        result = run(
            "$global:calls = @(); function Invoke-RestMethod { param($Method, $Uri, $Headers, $ContentType, $Body) "
            "$global:calls += $Uri; if ($Uri -like '*/enrollment') { [pscustomobject]@{ enrollment_code = '"
            + CODE + "' } } }; $t = ConvertTo-SecureString 'admin-secret' -AsPlainText -Force; "
            f"$c = & '{ISSUE}' -HubUrl 'https://hub.invalid/' -AdminToken $t; "
            "$calls -join '|'; $c.GetType().Name; "
            "([System.Net.NetworkCredential]::new('', $c).Password -ceq '" + CODE + "')")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("https://hub.invalid/admin/devices/create|https://hub.invalid/admin/devices/enrollment",
                      result.stdout)
        self.assertIn("SecureString", result.stdout)
        self.assertIn("True", result.stdout)
        self.assertNotIn("admin-secret", result.stdout + result.stderr)
        self.assertNotIn(HEX, result.stdout)

    def test_clipboard_mode_copies_the_code_and_prints_nothing_secret(self):
        result = run(
            "function Invoke-RestMethod { param($Method, $Uri, $Headers, $ContentType, $Body) "
            "if ($Uri -like '*/enrollment') { [pscustomobject]@{ enrollment_code = '" + CODE + "' } } }; "
            "$global:clip = $null; function Set-Clipboard { param($Value) $global:clip = $Value }; "
            "$t = ConvertTo-SecureString 'admin-secret' -AsPlainText -Force; "
            f"$out = & '{ISSUE}' -HubUrl 'https://hub.invalid' -AdminToken $t -CopyToClipboard; "
            "\"returned=$($null -ne $out) copied=$($global:clip -ceq '" + CODE + "')\"")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("returned=False copied=True", result.stdout)
        self.assertNotIn(HEX, result.stdout + result.stderr)
        self.assertNotIn("admin-secret", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
