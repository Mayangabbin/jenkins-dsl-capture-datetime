pipelineJob('record-datetime-to-postgres') {
    description('Job that runs dynamic Kubernetes pods and inserts current datetime into Postgres every 5 minutes with debug')
    triggers {
        cron('H/5 * * * *')
    }
    definition {
        cps {
            script("""
pipeline {
    agent {
        kubernetes {
            label 'dynamic-worker'
            defaultContainer 'python'
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: python
    image: python:3.11
    command:
    - sleep
    args:
    - "3600"
    env:
    - name: POSTGRES_PASSWORD
      valueFrom:
        secretKeyRef:
          name: postgres-postgresql
          key: postgresql-password
  restartPolicy: Never
'''
        }
    }

    stages {
        stage('Insert datetime with debug') {
            steps {
                container('python') {
                    sh '''
echo "Installing psycopg2-binary..."
pip install psycopg2-binary

echo "Running Python script..."
python - << 'EOF'
import os
from datetime import datetime
import psycopg2
import traceback

try:
    password = os.getenv("POSTGRES_PASSWORD")
    print(f"POSTGRES_PASSWORD set: {'YES' if password else 'NO'}")
    
    conn = psycopg2.connect(
        host='postgres-postgresql.demo.svc.cluster.local',
        database='postgres',
        user='postgres',
        password=password
    )
    print("Connected to Postgres successfully!")

    cur = conn.cursor()
    cur.execute("CREATE TABLE IF NOT EXISTS datetime_log (ts TIMESTAMP);")
    print("Table ensured to exist.")

    now = datetime.now()
    cur.execute("INSERT INTO datetime_log (ts) VALUES (%s);", (now,))
    print(f"Inserted datetime: {now}")

    conn.commit()
    cur.close()
    conn.close()
    print("Connection closed cleanly.")
except Exception as e:
    print("Error occurred:")
    traceback.print_exc()
EOF
'''
                }
            }
        }
    }
}
            """)
            sandbox()
        }
    }
}
