import { Controller, Post} from '@nestjs/common'

@Controller('app')
class Hello {

  @Post('/test')
  async doSomething() {
    return true;
  }
}
