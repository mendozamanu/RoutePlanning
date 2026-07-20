# Oracle Cloud deployment

This deployment runs OpenTripPlanner, the FastAPI facade and Caddy on one OCI Compute
instance. Only ports 80 and 443 are published. Caddy obtains and renews the public TLS
certificate, while FastAPI and OTP remain inside the Docker network.

## 1. Create the OCI instance

In the OCI Console, create a `VM.Standard.A1.Flex` instance in the tenancy home region with:

- Ubuntu 24.04 (ARM64)
- 2 OCPUs and 12 GB RAM
- At least a 50 GB boot volume
- A public IPv4 address
- Your SSH public key

Create a Network Security Group or subnet security-list rules with these stateful ingress
ports:

| Port | Source | Purpose |
| --- | --- | --- |
| TCP 22 | Your public IP `/32` | SSH administration |
| TCP 80 | `0.0.0.0/0` | ACME challenge and HTTPS redirect |
| TCP 443 | `0.0.0.0/0` | HTTPS API |
| UDP 443 | `0.0.0.0/0` | HTTP/3 (optional) |

Do not expose ports 8000 or 8080. Reserve the instance public IP if it must remain stable.

## 2. Configure DNS

Create an `A` record such as `api.example.com` pointing to the instance public IPv4 address.
Wait until it resolves publicly before starting Caddy:

```bash
dig +short api.example.com
```

The domain can be managed by OCI DNS or another DNS provider.

## 3. Install Docker

Connect to the instance:

```bash
ssh -i ~/.ssh/cordoba-connect.key ubuntu@INSTANCE_PUBLIC_IP
```

Install Docker Engine and the Compose plugin using Docker's official Ubuntu repository:

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

cat <<EOF | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Sign out and reconnect so the Docker group membership is applied, then verify:

```bash
docker run --rm hello-world
docker compose version
```

## 4. Upload the project and OTP graph

Clone the public repository on the instance:

```bash
git clone YOUR_GITHUB_REPOSITORY_URL cordoba-connect
cd cordoba-connect
```

The generated OTP graph is intentionally ignored by Git. From the Windows development
computer, upload the graph built and tested locally:

```powershell
scp -i C:\path\cordoba-connect.key `
  .\infra\otp\data\graph.obj `
  ubuntu@INSTANCE_PUBLIC_IP:~/cordoba-connect/infra/otp/data/graph.obj
```

## 5. Configure and start the stack

On the instance:

```bash
cd ~/cordoba-connect
cp deploy/oracle/.env.example deploy/oracle/.env
nano deploy/oracle/.env
sh deploy/oracle/deploy.sh
```

Set `API_DOMAIN` to the DNS name from step 2 and use a real contact address for
`ACME_EMAIL`. Keep `TRANSIT_DATA_EXPIRES_ON` aligned with the deployed GTFS snapshot.

Check the containers and public health endpoint:

```bash
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml ps
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml logs --tail=100
curl --fail https://api.example.com/health
```

## 6. Point Android at HTTPS

Update only the ignored local Android configuration:

```properties
API_BASE_URL=https://api.example.com
```

Rebuild and sign the release APK. No cleartext exception is needed for the public API.

## Updating

```bash
cd ~/cordoba-connect
git pull --ff-only
sh deploy/oracle/deploy.sh
```

Upload a newly built `graph.obj` before restarting when the OSM or GTFS inputs change.

## Operational notes

- Keep the OCI firewall limited to 22, 80 and 443 as described above.
- Apply Ubuntu and Docker security updates regularly.
- Monitor `docker compose logs`, disk usage and the `/health` endpoint.
- The public journey endpoint currently has no user authentication. Add rate limiting and
  Firebase App Check verification before operating it as a high-traffic production API.
