# Get koju-api's Swagger schema f.e. with curl
```
curl --location -k -s -H "API-KEY: ${RIPA_API_KEY}" "${RIPA_URL}/railway/koju-api/v2/api-docs?group=0.2" | jq > koju-api_v2.json
```