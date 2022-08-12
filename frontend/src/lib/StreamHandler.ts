import _ from 'lodash';
import { ReadableStreamDefaultReadResult } from 'stream/web';

const decoder = new TextDecoder();

export function StreamHandler(readableStream: ReadableStream<Uint8Array>, props: { 
  onClose: () => void,
  onAppend: (_: any[]) => void,
  onError: (_: any) => void
}) {
  const reader = readableStream.getReader();
  const { onClose, onAppend, onError } = props;

  let previous = "";

  function pump(): any {
    return reader.read().then((p: ReadableStreamDefaultReadResult<Uint8Array>) => {
      // before you ask, value is always undefined when done is true
      // https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream/getReader
      if (p.done) {
        onClose();
        return null;
      }

      const text = previous + decoder.decode(p.value);
      let toAppend: any[] = [];

      // ALGO:
      // Read text to \n
      // - if it exists >> doParse and append
      // - if not good >> store in previous, wait for that next shit
      function readAllText(v: string) {
        const idx = v.indexOf("\n");

        if (idx === -1) {
          previous = v;
        } else {
          const cleanedValue = v.substring(0, idx + 1).trim();
          if (cleanedValue.length > 0) {
            const item = JSON.parse(cleanedValue);
            // nextValue
            // do append
            if (item.error) {
              // no need to cancel as can assume server will do cancelling
              onError && onError(item);
            }

            if(!item.ignore) {
              toAppend.push(item);
            }
          }

          const nextValue = v.substring(idx + 1);          
          readAllText(nextValue);
        }
      }

      readAllText(text)

      if (toAppend.length > 0) {
        onAppend(toAppend);
      }

      return pump();
    });
  }

  return pump();
}
