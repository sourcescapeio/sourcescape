import { NestFactory } from '@nestjs/core';
import { Transport, MicroserviceOptions } from '@nestjs/microservices';
import { WorkerModule } from './worker/worker.module';


async function bootstrap() {
  const port = parseInt(process.argv[2]);

  const app = await NestFactory.createMicroservice<MicroserviceOptions>(
    WorkerModule,
    {
      transport: Transport.TCP,
      options: {
        port
      }
    },
  );
  await app.listen();
}
bootstrap();
