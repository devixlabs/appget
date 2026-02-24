#!/bin/bash
# Auto-generated API test script from openapi.yaml
# Generated: Mon Feb 16 19:15:18 CST 2026

set -e  # Exit on error

BASE_URL="http://localhost:8080"
CREATED_IDS=()

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}API Test Script${NC}"
echo -e "${BLUE}Base URL: $BASE_URL${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# ============================================================
# Role Tests
# ============================================================

echo -e "${BLUE}Testing: Create Role${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/roles" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"name": "Name"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /roles - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  ROLE_ID="$ID"
else
  echo -e "${RED}✗ POST /roles - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Role records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/roles")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /roles - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /roles - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$ROLE_ID" ]; then
  echo -e "${BLUE}Testing: Get Role by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/roles/$ROLE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /roles/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /roles/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$ROLE_ID" ]; then
  echo -e "${BLUE}Testing: Update Role${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/roles/$ROLE_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"name": "UpdatedName"}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /roles/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /roles/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$ROLE_ID" ]; then
  echo -e "${BLUE}Testing: Delete Role${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/roles/$ROLE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /roles/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /roles/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Employee Tests
# ============================================================

echo -e "${BLUE}Testing: Create Employee${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/employees" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"name": "Name", "age": 42, "roleId": "RoleId", "countryOfOrigin": "CountryOfOrigin"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /employees - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  EMPLOYEE_ID="$ID"
else
  echo -e "${RED}✗ POST /employees - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Employee records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/employees")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /employees - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /employees - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$EMPLOYEE_ID" ]; then
  echo -e "${BLUE}Testing: Get Employee by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/employees/$EMPLOYEE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /employees/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /employees/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$EMPLOYEE_ID" ]; then
  echo -e "${BLUE}Testing: Update Employee${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/employees/$EMPLOYEE_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"name": "UpdatedName", "age": 42, "roleId": "UpdatedRoleId", "countryOfOrigin": "UpdatedCountryOfOrigin"}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /employees/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /employees/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$EMPLOYEE_ID" ]; then
  echo -e "${BLUE}Testing: Delete Employee${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/employees/$EMPLOYEE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /employees/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /employees/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Vendor Tests
# ============================================================

echo -e "${BLUE}Testing: Create Vendor${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/vendors" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"name": "Name", "locationId": 1}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /vendors - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  VENDOR_ID="$ID"
else
  echo -e "${RED}✗ POST /vendors - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Vendor records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/vendors")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /vendors - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /vendors - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$VENDOR_ID" ]; then
  echo -e "${BLUE}Testing: Get Vendor by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/vendors/$VENDOR_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /vendors/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /vendors/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$VENDOR_ID" ]; then
  echo -e "${BLUE}Testing: Update Vendor${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/vendors/$VENDOR_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"name": "UpdatedName", "locationId": 1}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /vendors/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /vendors/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$VENDOR_ID" ]; then
  echo -e "${BLUE}Testing: Delete Vendor${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/vendors/$VENDOR_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /vendors/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /vendors/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Location Tests
# ============================================================

echo -e "${BLUE}Testing: Create Location${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/locations" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"locationid": "Locationid", "locationname": "Locationname", "longitude": 99.99, "latitude": 99.99}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /locations - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  LOCATION_ID="$ID"
else
  echo -e "${RED}✗ POST /locations - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Location records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/locations")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /locations - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /locations - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$LOCATION_ID" ]; then
  echo -e "${BLUE}Testing: Get Location by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/locations/$LOCATION_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /locations/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /locations/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$LOCATION_ID" ]; then
  echo -e "${BLUE}Testing: Update Location${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/locations/$LOCATION_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"locationid": "UpdatedLocationid", "locationname": "UpdatedLocationname", "longitude": 99.99, "latitude": 99.99}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /locations/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /locations/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$LOCATION_ID" ]; then
  echo -e "${BLUE}Testing: Delete Location${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/locations/$LOCATION_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /locations/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /locations/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Invoice Tests
# ============================================================

echo -e "${BLUE}Testing: Create Invoice${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/invoices" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"invoiceNumber": "InvoiceNumber", "amount": 99.99, "issueDate": "IssueDate"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /invoices - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  INVOICE_ID="$ID"
else
  echo -e "${RED}✗ POST /invoices - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Invoice records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/invoices")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /invoices - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /invoices - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$INVOICE_ID" ]; then
  echo -e "${BLUE}Testing: Get Invoice by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/invoices/$INVOICE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /invoices/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /invoices/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$INVOICE_ID" ]; then
  echo -e "${BLUE}Testing: Update Invoice${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/invoices/$INVOICE_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"invoiceNumber": "UpdatedInvoiceNumber", "amount": 99.99, "issueDate": "UpdatedIssueDate"}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /invoices/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /invoices/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$INVOICE_ID" ]; then
  echo -e "${BLUE}Testing: Delete Invoice${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/invoices/$INVOICE_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /invoices/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /invoices/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Department Tests
# ============================================================

echo -e "${BLUE}Testing: Create Department${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/departments" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"id": "Id", "name": "Name", "budget": 99.99}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /departments - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  DEPARTMENT_ID="$ID"
else
  echo -e "${RED}✗ POST /departments - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Department records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/departments")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /departments - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /departments - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$DEPARTMENT_ID" ]; then
  echo -e "${BLUE}Testing: Get Department by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/departments/$DEPARTMENT_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /departments/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /departments/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$DEPARTMENT_ID" ]; then
  echo -e "${BLUE}Testing: Update Department${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/departments/$DEPARTMENT_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"id": "UpdatedId", "name": "UpdatedName", "budget": 99.99}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /departments/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /departments/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$DEPARTMENT_ID" ]; then
  echo -e "${BLUE}Testing: Delete Department${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/departments/$DEPARTMENT_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /departments/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /departments/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


# ============================================================
# Salary Tests
# ============================================================

echo -e "${BLUE}Testing: Create Salary${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/salarys" \
  -H 'Content-Type: application/json' \
  -H 'X-Sso-Authenticated: true' \
  -H 'X-Roles-Role-Level: 5' \
  -d '{"employeeId": "EmployeeId", "amount": 99.99, "yearsOfService": 42}')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ POST /salarys - Created (201)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  # Extract ID for later tests
  ID=$(echo "$BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')" 2>/dev/null || echo "test-id")
  SALARY_ID="$ID"
else
  echo -e "${RED}✗ POST /salarys - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

echo -e "${BLUE}Testing: List Salary records${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/salarys")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo -e "${GREEN}✓ GET /salarys - Success (200)${NC}"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo -e "${RED}✗ GET /salarys - Failed ($HTTP_CODE)${NC}"
  echo "$BODY"
fi
echo ""

if [ -n "$SALARY_ID" ]; then
  echo -e "${BLUE}Testing: Get Salary by ID${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/salarys/$SALARY_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ GET /salarys/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ GET /salarys/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$SALARY_ID" ]; then
  echo -e "${BLUE}Testing: Update Salary${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/salarys/$SALARY_ID" \
    -H 'Content-Type: application/json' \
    -H 'X-Sso-Authenticated: true' \
    -H 'X-Roles-Role-Level: 5' \
    -d '{"employeeId": "UpdatedEmployeeId", "amount": 99.99, "yearsOfService": 42}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PUT /salarys/{id} - Success (200)${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
  else
    echo -e "${RED}✗ PUT /salarys/{id} - Failed ($HTTP_CODE)${NC}"
    echo "$BODY"
  fi
  echo ""
fi

if [ -n "$SALARY_ID" ]; then
  echo -e "${BLUE}Testing: Delete Salary${NC}"
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/salarys/$SALARY_ID")
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  if [ "$HTTP_CODE" -eq 204 ]; then
    echo -e "${GREEN}✓ DELETE /salarys/{id} - Success (204)${NC}"
  else
    echo -e "${RED}✗ DELETE /salarys/{id} - Failed ($HTTP_CODE)${NC}"
  fi
  echo ""
fi


echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All tests completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
