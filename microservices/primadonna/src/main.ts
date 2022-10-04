import { NestFactory } from '@nestjs/core';
import { MainModule } from './main/main.module';

async function bootstrap() {
  const app = await NestFactory.create(MainModule);
  await app.listen(3000);
}
bootstrap();
