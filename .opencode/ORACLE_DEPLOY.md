# Oracle Cloud deploy runbook — FlashcardQuiz

Host: Oracle Cloud Always Free, Ampere A1 (arm64), Caddy HTTPS in front of Docker Compose.

- SSH key (private, empty passphrase): `C:\Users\callm\.ssh\flashcardquiz_oracle`
- SSH key (public — paste into the instance): `C:\Users\callm\.ssh\flashcardquiz_oracle.pub`
- Project root: `F:\Downloads\flashcardquiz-master (copy)\flashcardquiz-master (copy)`
- Compose file: `docker-compose.yml` (web + caddy), config: `Caddyfile`

---

## Part A — you do this in the browser (~15 min)

### A1. Sign up
1. https://www.oracle.com/cloud/free/ → **Start for free**.
2. Country, email, password, (optional) company → **Verify email** with the code.
3. Choose your **home region** — it cannot be changed later.
   Recommended: **Germany East (eu-frankfurt-1)** or **US Ashburn (us-ashburn-1)**
   (most Ampere A1 capacity).
4. Account type: **Individual** → accept the agreement.
5. Add a credit/debit card for identity verification. Nothing is charged while
   you stay inside Always Free limits (card details are held, not billed).
6. Wait for the account to activate (usually minutes, occasionally a few hours).

### A2. Create the VM
Oracle Cloud menu → **Compute** → **Instances** → **Create instance**

| Field | Value |
|---|---|
| Name | `flashcardquiz` |
| Placement / compartment | default (root) |
| Image | **Ubuntu 24.04 (aarch64 / Arm)** — under "Change image" |
| Shape | **Change shape** → **Ampere A1** → Flex → **1 OCPU, 6 GB** |
| Networking | *Create new virtual cloud network* → **Create VCN plus related resources** (VCN wizard) |
| Add SSH keys | **Paste public keys** → paste `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINl1eBiXcUpBSMC6xdcVjBqVPNY+YZvDav5k0JtUldnj flashcardquiz-oracle` |
| Boot volume | default 50 GB |
| | **Create** |

### A3. Open the firewall
While the instance is being created (or after):
**Networking → Virtual cloud networks → (your VCN) → Security lists → Default security list → Add ingress rules**

| Source CIDR | IP protocol | Destination port range |
|---|---|---|
| `0.0.0.0/0` | TCP | `22` |
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

### A4. DNS
At your domain registrar, add:

```
Type  Name         Value                TTL
A     flashcards   <instance public IP> 300
```

(Any hostname works — `flashcards` is what we'll use below.)

### A5. Report back
Send me: **public IP** + **the hostname you created** (e.g. `flashcards.example.com`).

---

## Part B — I do this over SSH

1. `scp` a tarball of the project (excludes `android/`, `dist/`, `.git`, session dumps).
2. Write `/opt/flashcardquiz/.env`:
   ```
   OPENAI_API_KEY=free
   OPENAI_BASE_URL=https://text.pollinations.ai/openai
   LLM_MODEL=openai-fast
   MOCK_LLM=false
   OCR_PROVIDER=auto
   OCRSPACE_API_KEY=<key>
   MAX_UPLOAD_MB=50
   CORS_ORIGINS=
   SITE_ADDRESS=<hostname>
   ```
3. `docker compose up -d --build` (image is built **on** the arm64 VM).
4. `ufw allow 22,80,443/tcp` (Ubuntu image ships ufw inactive — Oracle security
   lists in A3 are the control that actually matters).
5. Verify: `/api/settings`, `/api/generate` (mock-free), `/api/ocr`, `/`.
6. Set `BACKEND_URL=https://<hostname>` in `android/local.properties`, rebuild
   the release APK, drop it in `dist/` so `/api/download/android` serves it.

## Known limitations (by design)
- The in-app settings UI cannot override `LLM_MODEL` / `OPENAI_BASE_URL` in
  Docker: compose puts them in the process environment, which always wins over
  the `.env` file the UI writes. Change them in `docker-compose.yml` instead.
- Deck data lives in the `flashcardquiz_data` Docker volume (`/data`).
  Back it up with `docker run --rm -v flashcardquiz_data:/d alpine tar czf - /d > data.tgz`.
